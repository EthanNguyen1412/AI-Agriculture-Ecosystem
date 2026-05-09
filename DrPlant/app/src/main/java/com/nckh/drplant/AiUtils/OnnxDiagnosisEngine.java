package com.nckh.drplant.AiUtils;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.nckh.drplant.Models.DiagnosisResponse;
import com.nckh.drplant.Utils.OnnxDiagnosisMapper;

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
    private static final String[] CLASS_NAMES = new String[]{
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

    private OnnxDiagnosisEngine(@NonNull Context context) throws IOException, OrtException {
        ortEnvironment = OrtEnvironment.getEnvironment();
        ortSession = ortEnvironment.createSession(readModelBytes(context.getApplicationContext()), new OrtSession.SessionOptions());
    }

    // Trả về singleton của engine để tái sử dụng session ONNX và tránh load model lặp lại nhiều lần.
    @NonNull
    public static OnnxDiagnosisEngine getInstance(@NonNull Context context) throws IOException, OrtException {
        if (instance == null) {
            synchronized (OnnxDiagnosisEngine.class) {
                if (instance == null) {
                    instance = new OnnxDiagnosisEngine(context);
                }
            }
        }
        return instance;
    }

    // Chạy toàn bộ pipeline local gồm preprocess, suy luận và ánh xạ kết quả sang DiagnosisResponse cho UI hiện tại.
    @NonNull
    public DiagnosisResponse diagnoseImage(@NonNull File imageFile) throws Exception {
        DetectionResult detectionResult = detectBestLeaf(imageFile);
        return OnnxDiagnosisMapper.mapToDiagnosisResponse(detectionResult);
    }

    // Thực hiện suy luận YOLO trên ảnh đầu vào và trả về detection tốt nhất sau bước non-max suppression.
    @Nullable
    public DetectionResult detectBestLeaf(@NonNull File imageFile) throws Exception {
        OnnxImagePreprocessor.PreprocessedImage preprocessedImage =
                OnnxImagePreprocessor.preprocessImageFile(imageFile, INPUT_WIDTH, INPUT_HEIGHT);

        long[] inputShape = new long[]{1, 3, INPUT_HEIGHT, INPUT_WIDTH};
        try (OnnxTensor inputTensor = OnnxTensor.createTensor(
                ortEnvironment,
                FloatBuffer.wrap(preprocessedImage.tensorData),
                inputShape
        );
             OrtSession.Result result = ortSession.run(Collections.singletonMap(INPUT_NAME, inputTensor))) {
            float[][][] outputTensor = extractOutputTensor(result.get(0).getValue());
            List<DetectionResult> detections = decodeDetections(outputTensor[0], preprocessedImage);
            if (detections.isEmpty()) {
                return null;
            }

            Collections.sort(detections, Comparator.comparingDouble(detection -> -detection.confidence));
            return detections.get(0);
        }
    }

    // Đọc output tensor từ model và kiểm tra đúng định dạng [1, 4 + numClasses, numPredictions].
    @NonNull
    private float[][][] extractOutputTensor(@Nullable Object rawOutput) {
        if (rawOutput instanceof float[][][]) {
            return (float[][][]) rawOutput;
        }
        throw new IllegalStateException("Định dạng output của model không hợp lệ: " + (rawOutput == null ? "null" : rawOutput.getClass().getSimpleName()));
    }

    // Giải mã tensor YOLOv8 detect thành danh sách bounding box có class/confidence rồi áp dụng NMS.
    @NonNull
    private List<DetectionResult> decodeDetections(@NonNull float[][] modelOutput,
                                                   @NonNull OnnxImagePreprocessor.PreprocessedImage preprocessedImage) {
        if (modelOutput.length < 5) {
            return new ArrayList<>();
        }

        int candidateCount = modelOutput[0].length;
        int classCount = modelOutput.length - 4;
        List<DetectionResult> detections = new ArrayList<>();

        for (int candidateIndex = 0; candidateIndex < candidateCount; candidateIndex++) {
            int bestClassIndex = -1;
            float bestClassScore = 0f;
            for (int classOffset = 0; classOffset < classCount; classOffset++) {
                float classScore = modelOutput[classOffset + 4][candidateIndex];
                if (classScore > bestClassScore) {
                    bestClassScore = classScore;
                    bestClassIndex = classOffset;
                }
            }

            if (bestClassIndex < 0 || bestClassScore < SCORE_THRESHOLD) {
                continue;
            }

            float centerX = modelOutput[0][candidateIndex];
            float centerY = modelOutput[1][candidateIndex];
            float width = modelOutput[2][candidateIndex];
            float height = modelOutput[3][candidateIndex];

            float left = ((centerX - (width / 2f)) - preprocessedImage.padLeft) / preprocessedImage.scale;
            float top = ((centerY - (height / 2f)) - preprocessedImage.padTop) / preprocessedImage.scale;
            float right = ((centerX + (width / 2f)) - preprocessedImage.padLeft) / preprocessedImage.scale;
            float bottom = ((centerY + (height / 2f)) - preprocessedImage.padTop) / preprocessedImage.scale;

            left = clamp(left, 0f, preprocessedImage.sourceWidth);
            top = clamp(top, 0f, preprocessedImage.sourceHeight);
            right = clamp(right, 0f, preprocessedImage.sourceWidth);
            bottom = clamp(bottom, 0f, preprocessedImage.sourceHeight);

            if (right <= left || bottom <= top) {
                continue;
            }

            String className = bestClassIndex < CLASS_NAMES.length ? CLASS_NAMES[bestClassIndex] : "unknown";
            DetectionResult detectionResult = new DetectionResult(
                    bestClassIndex,
                    className,
                    bestClassScore * 100d,
                    left,
                    top,
                    right,
                    bottom,
                    resolvePlantType(className),
                    isHealthyClass(className)
            );
            detections.add(detectionResult);
        }

        return applyNonMaxSuppression(detections);
    }

    // Loại bỏ các bounding box trùng nhau bằng NMS để giữ lại vùng lá tốt nhất cho từng bệnh.
    @NonNull
    private List<DetectionResult> applyNonMaxSuppression(@NonNull List<DetectionResult> detections) {
        if (detections.isEmpty()) {
            return detections;
        }

        List<DetectionResult> sortedDetections = new ArrayList<>(detections);
        Collections.sort(sortedDetections, Comparator.comparingDouble(detection -> -detection.confidence));
        List<DetectionResult> keptDetections = new ArrayList<>();

        boolean[] removed = new boolean[sortedDetections.size()];
        for (int index = 0; index < sortedDetections.size(); index++) {
            if (removed[index]) {
                continue;
            }

            DetectionResult currentDetection = sortedDetections.get(index);
            keptDetections.add(currentDetection);
            for (int nextIndex = index + 1; nextIndex < sortedDetections.size(); nextIndex++) {
                if (removed[nextIndex]) {
                    continue;
                }

                DetectionResult nextDetection = sortedDetections.get(nextIndex);
                if (currentDetection.classIndex == nextDetection.classIndex
                        && calculateIoU(currentDetection, nextDetection) >= NMS_IOU_THRESHOLD) {
                    removed[nextIndex] = true;
                }
            }
        }

        return keptDetections;
    }

    // Tính IoU giữa hai bounding box để quyết định box nào bị loại trong bước NMS.
    private float calculateIoU(@NonNull DetectionResult firstDetection, @NonNull DetectionResult secondDetection) {
        float intersectionLeft = Math.max(firstDetection.left, secondDetection.left);
        float intersectionTop = Math.max(firstDetection.top, secondDetection.top);
        float intersectionRight = Math.min(firstDetection.right, secondDetection.right);
        float intersectionBottom = Math.min(firstDetection.bottom, secondDetection.bottom);

        float intersectionWidth = Math.max(0f, intersectionRight - intersectionLeft);
        float intersectionHeight = Math.max(0f, intersectionBottom - intersectionTop);
        float intersectionArea = intersectionWidth * intersectionHeight;
        if (intersectionArea <= 0f) {
            return 0f;
        }

        float firstArea = Math.max(0f, firstDetection.right - firstDetection.left)
                * Math.max(0f, firstDetection.bottom - firstDetection.top);
        float secondArea = Math.max(0f, secondDetection.right - secondDetection.left)
                * Math.max(0f, secondDetection.bottom - secondDetection.top);
        float unionArea = firstArea + secondArea - intersectionArea;
        if (unionArea <= 0f) {
            return 0f;
        }

        return intersectionArea / unionArea;
    }

    // Giới hạn một giá trị số thực vào trong khoảng cho phép để tránh bounding box vượt ra khỏi ảnh gốc.
    private float clamp(float value, float minValue, float maxValue) {
        return Math.max(minValue, Math.min(value, maxValue));
    }

    // Suy ra loại cây từ nhãn class của model để tính năng lọc lịch sử vẫn hoạt động chính xác.
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

    // Kiểm tra nhãn dự đoán có phải nhóm lá khỏe mạnh hay không để UI hiển thị trạng thái đúng.
    private boolean isHealthyClass(@NonNull String className) {
        return className.endsWith("_khoe");
    }

    // Đọc toàn bộ file model ONNX trong assets thành mảng byte để tạo OrtSession.
    @NonNull
    private byte[] readModelBytes(@NonNull Context context) throws IOException {
        try (InputStream inputStream = context.getAssets().open(MODEL_ASSET_NAME);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            return outputStream.toByteArray();
        }
    }

    // Lớp dữ liệu biểu diễn detection tốt nhất sau suy luận để mapper dựng ra response cho giao diện.
    public static final class DetectionResult {
        public final int classIndex;
        @NonNull public final String className;
        public final double confidence;
        public final float left;
        public final float top;
        public final float right;
        public final float bottom;
        @NonNull public final String plantType;
        public final boolean healthy;

        DetectionResult(int classIndex,
                        @NonNull String className,
                        double confidence,
                        float left,
                        float top,
                        float right,
                        float bottom,
                        @NonNull String plantType,
                        boolean healthy) {
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
