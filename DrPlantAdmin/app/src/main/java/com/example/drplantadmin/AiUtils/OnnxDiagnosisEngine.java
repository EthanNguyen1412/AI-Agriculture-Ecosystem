 package com.example.drplantadmin.AiUtils;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.drplantadmin.Models.DiagnosisResponse;
import com.example.drplantadmin.Utils.OnnxDiagnosisMapper;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

public final class OnnxDiagnosisEngine {

    private static final String MODEL_ASSET_NAME = "best.onnx";
    private static final String INPUT_NAME = "images";

    private static final int INPUT_WIDTH = 640;
    private static final int INPUT_HEIGHT = 640;

    private static final float SCORE_THRESHOLD = 0.25f;
    private static final float NMS_IOU_THRESHOLD = 0.45f;

    private static final String[] CLASS_NAMES = {
            "cafe_gisat",
            "cafe_dommatcua",
            "cafe_khoe",
            "saurieng_chayla",
            "saurieng_domtao",
            "saurieng_khoe"
    };

    private static volatile OnnxDiagnosisEngine instance;

    private final OrtEnvironment ortEnvironment;
    private final OrtSession ortSession;

    private OnnxDiagnosisEngine(@NonNull Context context)
            throws IOException, OrtException {

        ortEnvironment = OrtEnvironment.getEnvironment();

        OrtSession.SessionOptions options =
                new OrtSession.SessionOptions();

        try {
            ortSession = ortEnvironment.createSession(
                    readModelBytes(context.getApplicationContext()),
                    options
            );
        } finally {
            options.close();
        }
    }

    @NonNull
    public static OnnxDiagnosisEngine getInstance(
            @NonNull Context context
    ) throws IOException, OrtException {

        if (instance == null) {
            synchronized (OnnxDiagnosisEngine.class) {
                if (instance == null) {
                    instance = new OnnxDiagnosisEngine(context);
                }
            }
        }

        return instance;
    }

    @NonNull
    public DiagnosisResponse diagnoseImage(@NonNull File imageFile)
            throws Exception {

        DetectionResult detectionResult = detectBestLeaf(imageFile);

        return OnnxDiagnosisMapper.mapToDiagnosisResponse(
                detectionResult
        );
    }

    @Nullable
    public DetectionResult detectBestLeaf(@NonNull File imageFile)
            throws Exception {

        if (!imageFile.exists() || !imageFile.isFile()) {
            throw new IOException("Không tìm thấy file ảnh đầu vào.");
        }

        OnnxImagePreprocessor.PreprocessedImage preprocessedImage =
                OnnxImagePreprocessor.preprocessImageFile(
                        imageFile,
                        INPUT_WIDTH,
                        INPUT_HEIGHT
                );

        long[] inputShape = {
                1,
                3,
                INPUT_HEIGHT,
                INPUT_WIDTH
        };

        try (OnnxTensor inputTensor = OnnxTensor.createTensor(
                ortEnvironment,
                FloatBuffer.wrap(preprocessedImage.tensorData),
                inputShape
        );
             OrtSession.Result result = ortSession.run(
                     Collections.singletonMap(INPUT_NAME, inputTensor)
             )) {

            float[][][] outputTensor =
                    extractOutputTensor(result.get(0).getValue());

            List<DetectionResult> detections =
                    decodeDetections(outputTensor[0], preprocessedImage);

            if (detections.isEmpty()) {
                return null;
            }

            Collections.sort(
                    detections,
                    Comparator.comparingDouble(
                            detection -> -detection.confidence
                    )
            );

            return detections.get(0);
        }
    }

    @NonNull
    private float[][][] extractOutputTensor(@Nullable Object rawOutput) {

        if (rawOutput instanceof float[][][]) {
            return (float[][][]) rawOutput;
        }

        throw new IllegalStateException(
                "Định dạng output của model không hợp lệ: "
                        + (rawOutput == null
                        ? "null"
                        : rawOutput.getClass().getSimpleName())
        );
    }

    @NonNull
    private List<DetectionResult> decodeDetections(
            @NonNull float[][] modelOutput,
            @NonNull OnnxImagePreprocessor.PreprocessedImage image
    ) {

        List<DetectionResult> detections = new ArrayList<>();

        if (modelOutput.length < 5 || modelOutput[0].length == 0) {
            return detections;
        }

        int candidateCount = modelOutput[0].length;
        int classCount = Math.min(
                modelOutput.length - 4,
                CLASS_NAMES.length
        );

        for (int candidateIndex = 0;
             candidateIndex < candidateCount;
             candidateIndex++) {

            int bestClassIndex = -1;
            float bestClassScore = 0f;

            for (int classOffset = 0;
                 classOffset < classCount;
                 classOffset++) {

                float classScore =
                        modelOutput[classOffset + 4][candidateIndex];

                if (Float.isFinite(classScore)
                        && classScore > bestClassScore) {

                    bestClassScore = classScore;
                    bestClassIndex = classOffset;
                }
            }

            if (bestClassIndex < 0
                    || bestClassScore < SCORE_THRESHOLD) {
                continue;
            }

            float centerX = modelOutput[0][candidateIndex];
            float centerY = modelOutput[1][candidateIndex];
            float width = modelOutput[2][candidateIndex];
            float height = modelOutput[3][candidateIndex];

            if (!Float.isFinite(centerX)
                    || !Float.isFinite(centerY)
                    || !Float.isFinite(width)
                    || !Float.isFinite(height)
                    || width <= 0f
                    || height <= 0f) {
                continue;
            }

            float left = (
                    centerX - width / 2f - image.padLeft
            ) / image.scale;

            float top = (
                    centerY - height / 2f - image.padTop
            ) / image.scale;

            float right = (
                    centerX + width / 2f - image.padLeft
            ) / image.scale;

            float bottom = (
                    centerY + height / 2f - image.padTop
            ) / image.scale;

            left = clamp(left, 0f, image.sourceWidth);
            top = clamp(top, 0f, image.sourceHeight);
            right = clamp(right, 0f, image.sourceWidth);
            bottom = clamp(bottom, 0f, image.sourceHeight);

            if (right <= left || bottom <= top) {
                continue;
            }

            String className = CLASS_NAMES[bestClassIndex];

            detections.add(
                    new DetectionResult(
                            bestClassIndex,
                            className,
                            bestClassScore * 100d,
                            left,
                            top,
                            right,
                            bottom,
                            resolvePlantType(className),
                            isHealthyClass(className)
                    )
            );
        }

        return applyNonMaxSuppression(detections);
    }

    @NonNull
    private List<DetectionResult> applyNonMaxSuppression(
            @NonNull List<DetectionResult> detections
    ) {

        if (detections.isEmpty()) {
            return detections;
        }

        List<DetectionResult> sortedDetections =
                new ArrayList<>(detections);

        Collections.sort(
                sortedDetections,
                Comparator.comparingDouble(
                        detection -> -detection.confidence
                )
        );

        List<DetectionResult> keptDetections = new ArrayList<>();

        boolean[] removed = new boolean[sortedDetections.size()];

        for (int i = 0; i < sortedDetections.size(); i++) {

            if (removed[i]) {
                continue;
            }

            DetectionResult current = sortedDetections.get(i);
            keptDetections.add(current);

            for (int j = i + 1;
                 j < sortedDetections.size();
                 j++) {

                if (removed[j]) {
                    continue;
                }

                DetectionResult next = sortedDetections.get(j);

                if (current.classIndex == next.classIndex
                        && calculateIoU(current, next)
                        >= NMS_IOU_THRESHOLD) {

                    removed[j] = true;
                }
            }
        }

        return keptDetections;
    }

    private float calculateIoU(
            @NonNull DetectionResult first,
            @NonNull DetectionResult second
    ) {

        float intersectionLeft =
                Math.max(first.left, second.left);

        float intersectionTop =
                Math.max(first.top, second.top);

        float intersectionRight =
                Math.min(first.right, second.right);

        float intersectionBottom =
                Math.min(first.bottom, second.bottom);

        float intersectionWidth = Math.max(
                0f,
                intersectionRight - intersectionLeft
        );

        float intersectionHeight = Math.max(
                0f,
                intersectionBottom - intersectionTop
        );

        float intersectionArea =
                intersectionWidth * intersectionHeight;

        if (intersectionArea <= 0f) {
            return 0f;
        }

        float firstArea =
                Math.max(0f, first.right - first.left)
                        * Math.max(0f, first.bottom - first.top);

        float secondArea =
                Math.max(0f, second.right - second.left)
                        * Math.max(0f, second.bottom - second.top);

        float unionArea =
                firstArea + secondArea - intersectionArea;

        if (unionArea <= 0f) {
            return 0f;
        }

        return intersectionArea / unionArea;
    }

    private float clamp(
            float value,
            float minValue,
            float maxValue
    ) {
        return Math.max(minValue, Math.min(value, maxValue));
    }

    @NonNull
    private String resolvePlantType(@NonNull String className) {

        if (className.startsWith("cafe_")) {
            return "coffee";
        }

        if (className.startsWith("saurieng_")) {
            return "saurieng";
        }

        return "unknown";
    }

    private boolean isHealthyClass(@NonNull String className) {
        return className.endsWith("_khoe");
    }

    @NonNull
    private byte[] readModelBytes(@NonNull Context context)
            throws IOException {

        try (InputStream inputStream =
                     context.getAssets().open(MODEL_ASSET_NAME);
             ByteArrayOutputStream outputStream =
                     new ByteArrayOutputStream()) {

            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            return outputStream.toByteArray();
        }
    }

    public static final class DetectionResult {

        public final int classIndex;

        @NonNull
        public final String className;

        public final double confidence;
        public final float left;
        public final float top;
        public final float right;
        public final float bottom;

        @NonNull
        public final String plantType;

        public final boolean healthy;

        DetectionResult(
                int classIndex,
                @NonNull String className,
                double confidence,
                float left,
                float top,
                float right,
                float bottom,
                @NonNull String plantType,
                boolean healthy
        ) {
            this.classIndex = classIndex;
            this.className = className;
            this.confidence = confidence;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.plantType = plantType;
            this.healthy = healthy;
        }
    }
}