package com.example.drplantadmin;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.core.ImageCapture.OutputFileOptions;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.example.drplantadmin.AiUtils.OnnxDiagnosisEngine;
import com.example.drplantadmin.Models.DiagnosisResponse;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AiCameraActivity extends AppCompatActivity {

    private PreviewView previewView;
    private TextView txtStatus;
    private Button btnCapture, btnGallery;

    private ImageCapture imageCapture;
    private ExecutorService executor;
    private File imageDirectory;
    private boolean processing = false;

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (granted) {
                            startCamera();
                        } else {
                            Toast.makeText(this,
                                    "Bạn cần cấp quyền camera để chụp ảnh.",
                                    Toast.LENGTH_LONG).show();
                        }
                    });

    private final ActivityResultLauncher<String> galleryLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.GetContent(),
                    this::handleGalleryImage);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        executor = Executors.newSingleThreadExecutor();

        imageDirectory = new File(getFilesDir(), "diagnosis_images");
        if (!imageDirectory.exists()) {
            imageDirectory.mkdirs();
        }

        createInterface();

        btnCapture.setOnClickListener(v -> captureImage());
        btnGallery.setOnClickListener(v -> galleryLauncher.launch("image/*"));

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void createInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16, 16, 16, 16);
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("CHẨN ĐOÁN CÂY TRỒNG BẰNG AI");
        title.setTextSize(20);
        title.setTextColor(Color.rgb(35, 110, 65));
        title.setGravity(Gravity.CENTER);
        title.setPadding(4, 12, 4, 12);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        previewView = new PreviewView(this);
        previewView.setImplementationMode(
                PreviewView.ImplementationMode.COMPATIBLE);

        LinearLayout.LayoutParams previewParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        previewParams.setMargins(0, 12, 0, 12);
        root.addView(previewView, previewParams);

        txtStatus = new TextView(this);
        txtStatus.setText("Đưa lá cây vào khung hình rồi chụp ảnh.");
        txtStatus.setTextSize(15);
        txtStatus.setTextColor(Color.DKGRAY);
        txtStatus.setGravity(Gravity.CENTER);
        txtStatus.setPadding(8, 8, 8, 8);
        root.addView(txtStatus);

        btnCapture = new Button(this);
        btnCapture.setText("CHỤP ẢNH VÀ CHẨN ĐOÁN");
        root.addView(btnCapture);

        btnGallery = new Button(this);
        btnGallery.setText("CHỌN ẢNH TỪ THƯ VIỆN");
        root.addView(btnGallery);

        Button btnBack = new Button(this);
        btnBack.setText("QUAY LẠI");
        btnBack.setOnClickListener(v -> finish());
        root.addView(btnBack);

        setContentView(root);
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);

        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(
                                ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                provider.unbindAll();
                provider.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture
                );

            } catch (Exception e) {
                txtStatus.setText("Không thể mở camera: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void captureImage() {
        if (processing) return;

        if (imageCapture == null) {
            Toast.makeText(this, "Camera chưa sẵn sàng.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        File photo = new File(imageDirectory,
                "diagnosis_" + System.currentTimeMillis() + ".jpg");

        OutputFileOptions options =
                new OutputFileOptions.Builder(photo).build();

        setProcessing(true, "Đang chụp ảnh...");

        imageCapture.takePicture(
                options,
                ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(
                            ImageCapture.OutputFileResults output) {
                        runDiagnosis(photo);
                    }

                    @Override
                    public void onError(ImageCaptureException exception) {
                        setProcessing(false, "Chụp ảnh thất bại.");
                        Toast.makeText(AiCameraActivity.this,
                                exception.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    private void handleGalleryImage(Uri uri) {
        if (uri == null || processing) return;

        setProcessing(true, "Đang đọc ảnh...");
        executor.execute(() -> {
            File copiedImage = null;

            try {
                copiedImage = copyImageToPrivateStorage(uri);
                if (copiedImage == null) {
                    throw new Exception("Không đọc được ảnh đã chọn.");
                }

                File finalImage = copiedImage;
                runOnUiThread(() -> runDiagnosis(finalImage));

            } catch (Exception e) {
                runOnUiThread(() -> {
                    setProcessing(false, "Không thể đọc ảnh.");
                    Toast.makeText(this, e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private File copyImageToPrivateStorage(Uri uri) throws Exception {
        ContentResolver resolver = getContentResolver();

        File destination = new File(imageDirectory,
                "diagnosis_" + System.currentTimeMillis() + ".jpg");

        try (InputStream input = resolver.openInputStream(uri);
             FileOutputStream output = new FileOutputStream(destination)) {

            if (input == null) {
                throw new Exception("Không mở được ảnh.");
            }

            byte[] buffer = new byte[8192];
            int length;

            while ((length = input.read(buffer)) != -1) {
                output.write(buffer, 0, length);
            }
        }

        return destination;
    }

    private void runDiagnosis(File imageFile) {
        if (isFinishing() || isDestroyed()) return;

        setProcessing(true, "AI đang phân tích ảnh, vui lòng chờ...");

        executor.execute(() -> {
            try {
                DiagnosisResponse response =
                        OnnxDiagnosisEngine
                                .getInstance(getApplicationContext())
                                .diagnoseImage(imageFile);

                if (response == null
                        || !response.success
                        || response.prediction == null) {

                    String message = response != null
                            ? response.message
                            : "Không nhận được kết quả từ AI.";

                    runOnUiThread(() -> {
                        setProcessing(false, message);
                        Toast.makeText(this, message,
                                Toast.LENGTH_LONG).show();
                    });
                    return;
                }

                String plant = getPlantName(response);
                String disease = getDiseaseName(response);
                String status = response.prediction.isHealthy
                        ? "Khỏe mạnh" : "Có bệnh";
                String advice = buildCareAdvice(response);

                RecordDatabaseHelper databaseHelper =
                        new RecordDatabaseHelper(AiCameraActivity.this);

                long rowId = databaseHelper.addRecord(
                        plant,
                        status,
                        disease,
                        imageFile.getAbsolutePath(),
                        advice
                );

                runOnUiThread(() -> {
                    if (rowId > 0) {
                        Toast.makeText(this,
                                "Đã lưu kết quả chẩn đoán!",
                                Toast.LENGTH_LONG).show();
                        finish();
                    } else {
                        setProcessing(false,
                                "Không lưu được bản ghi vào cơ sở dữ liệu.");
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    setProcessing(false, "Chẩn đoán thất bại.");
                    Toast.makeText(this,
                            "Lỗi AI: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private String getPlantName(DiagnosisResponse response) {
        String plantType = response.prediction.plantType;

        if (plantType == null) return "Chưa xác định";
        if (plantType.equalsIgnoreCase("coffee")) return "Cà phê";
        if (plantType.equalsIgnoreCase("saurieng")) return "Sầu riêng";

        return plantType;
    }

    private String getDiseaseName(DiagnosisResponse response) {
        if (response.disease != null
                && response.disease.name != null
                && !response.disease.name.trim().isEmpty()) {
            return response.disease.name;
        }

        if (response.prediction.className != null) {
            return response.prediction.className;
        }

        return "Chưa xác định";
    }

    private String buildCareAdvice(DiagnosisResponse response) {
        StringBuilder advice = new StringBuilder();

        if (response.disease != null) {
            appendAdvice(advice, "Triệu chứng",
                    response.disease.symptoms);
            appendAdvice(advice, "Nguyên nhân",
                    response.disease.causes);
        }

        if (response.solutions != null) {
            if (response.solutions.chemical != null) {
                appendAdvice(advice, "Biện pháp hóa học",
                        response.solutions.chemical.title);
                appendAdvice(advice, "Hướng dẫn",
                        response.solutions.chemical.description);
            }

            if (response.solutions.biological != null) {
                appendAdvice(advice, "Biện pháp sinh học",
                        response.solutions.biological.title);
                appendAdvice(advice, "Hướng dẫn",
                        response.solutions.biological.description);
            }
        }

        if (advice.length() == 0) {
            advice.append("Chưa có hướng dẫn chăm sóc cho kết quả này.");
        }

        return advice.toString();
    }

    private void appendAdvice(StringBuilder builder,
                              String title, String content) {
        if (content != null && !content.trim().isEmpty()) {
            if (builder.length() > 0) builder.append("\n\n");
            builder.append(title).append(": ").append(content);
        }
    }

    private void setProcessing(boolean busy, String message) {
        processing = busy;

        if (txtStatus != null) {
            txtStatus.setText(message);
        }

        if (btnCapture != null) btnCapture.setEnabled(!busy);
        if (btnGallery != null) btnGallery.setEnabled(!busy);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (executor != null) {
            executor.shutdown();
        }
    }
}
