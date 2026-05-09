package com.nckh.drplant;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import static android.Manifest.*;

import com.nckh.drplant.AiUtils.OnnxDiagnosisEngine;
import com.nckh.drplant.Models.DiagnosisResponse;
import com.nckh.drplant.Utils.DiagnosisHelper;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

public class CameraActivity extends AppCompatActivity {

    private static final String TAG = "CameraActivity";
    private static final String APP_GALLERY_DIRECTORY = "Pictures/DrPlant";

    ImageButton capture, toggeFlash, flipCamera, addFromGallery, historyRedirect;
    private LinearLayout uploadLoadingOverlay;
    private TextView uploadLoadingText;

    int cameraFacing = CameraSelector.LENS_FACING_BACK;
    private PreviewView previewView;
    private boolean isUploadingDiagnosis = false;
    private File pendingGallerySaveFile;
    private final ActivityResultLauncher<String> activityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            result -> {
                if (result) {
                    startCamera(cameraFacing);
                }
            });
    private final ActivityResultLauncher<String> galleryImagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            this::handleGalleryImagePicked
    );
    private final ActivityResultLauncher<String> storagePermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            result -> {
                if (result && pendingGallerySaveFile != null) {
                    saveCapturedImageToGalleryAsync(pendingGallerySaveFile);
                } else if (!result) {
                    Toast.makeText(this, R.string.camera_gallery_save_permission_denied, Toast.LENGTH_SHORT).show();
                }
                pendingGallerySaveFile = null;
            }
    );

    // Hàm khởi tạo màn hình camera, ánh xạ view và gán sự kiện cho các nút chính.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_camera);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        previewView = findViewById(R.id.previewView);
        capture = findViewById(R.id.capture);
        toggeFlash = findViewById(R.id.toggleFlash);
        historyRedirect = findViewById(R.id.historyRedirect);
        flipCamera = findViewById(R.id.flipCamera);
        addFromGallery = findViewById(R.id.addFromGallery);
        uploadLoadingOverlay = findViewById(R.id.uploadLoadingOverlay);
        uploadLoadingText = findViewById(R.id.uploadLoadingText);

        // Kiểm tra quyền truy cập camera
        if (ContextCompat.checkSelfPermission(this, permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startCamera(cameraFacing);
        } else {
            activityResultLauncher.launch(permission.CAMERA);
        }

        // Xử lý sự kiện khi người dùng nhấn nút chuyển đổi camera
        flipCamera.setOnClickListener(v -> {
            cameraFacing = (cameraFacing == CameraSelector.LENS_FACING_BACK)
                    ? CameraSelector.LENS_FACING_FRONT
                    : CameraSelector.LENS_FACING_BACK;
            startCamera(cameraFacing);
        });
        historyRedirect.setOnClickListener(v -> {
            if (isUploadingDiagnosis) {
                return;
            }
            startActivity(new Intent(CameraActivity.this, HistoryActivity.class));
        });
        addFromGallery.setOnClickListener(v -> openGalleryPicker());
    }

    // Mở bộ chọn ảnh của hệ thống để người dùng chọn ảnh từ thư viện.
    private void openGalleryPicker() {
        if (isUploadingDiagnosis) {
            return;
        }
        galleryImagePickerLauncher.launch("image/*");
    }

    // Nhận ảnh từ thư viện, sao chép về file cục bộ rồi tái sử dụng luồng chẩn đoán hiện có.
    private void handleGalleryImagePicked(Uri imageUri) {
        if (imageUri == null) {
            return;
        }

        setLoadingState(true);
        uploadLoadingText.setText(R.string.camera_gallery_processing);

        Thread galleryImportThread = new Thread(() -> {
            try {
                File localImageFile = copyGalleryImageToLocalFile(imageUri);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    uploadCapturedImage(localImageFile);
                });
            } catch (IOException exception) {
                Log.e(TAG, "Unable to import image from gallery", exception);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    setLoadingState(false);
                    Toast.makeText(CameraActivity.this, R.string.camera_gallery_import_failed, Toast.LENGTH_LONG).show();
                });
            }
        });
        galleryImportThread.start();
    }

    // Khởi động CameraX, cấu hình preview/chụp ảnh và gắn hành vi cho các nút camera.
    private void startCamera(int cameraFacing) {
        int aspectRatio = aspectRatio(previewView.getWidth(), previewView.getHeight());
        ListenableFuture<ProcessCameraProvider> listenableFuture = ProcessCameraProvider.getInstance(this);

        listenableFuture.addListener(() -> {
            try {

                ProcessCameraProvider cameraProvider = listenableFuture.get();

                Preview preview = new Preview.Builder()
                        .setTargetAspectRatio(aspectRatio)
                        .build();

                ImageCapture imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setTargetRotation(getWindowManager().getDefaultDisplay().getRotation())
                        .build();

                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(cameraFacing)
                        .build();

                cameraProvider.unbindAll();

                Camera camera = cameraProvider.bindToLifecycle(CameraActivity.this, cameraSelector, preview, imageCapture);

                capture.setOnClickListener(view -> {
                    if (isUploadingDiagnosis) {
                        return;
                    }

                    if (ContextCompat.checkSelfPermission(CameraActivity.this, permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        takePicture(imageCapture);
                    } else {
                        activityResultLauncher.launch(permission.CAMERA);
                    }
                });

                toggeFlash.setOnClickListener(view -> setFlashIcon(camera));

                preview.setSurfaceProvider(previewView.getSurfaceProvider());
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Unable to start camera", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // Tính tỉ lệ khung hình phù hợp nhất giữa 4:3 và 16:9 dựa trên kích thước preview.
    private int aspectRatio(int width, int height) {
        double previewRatio = (double) Math.max(width, height) / Math.min(width, height);
        if (Math.abs(previewRatio - 4.0 / 3.0) <= Math.abs(previewRatio - 16.0 / 9.0)) {
            return AspectRatio.RATIO_4_3;
        } else {
            return AspectRatio.RATIO_16_9;
        }
    }

    // Chụp ảnh và lưu tạm vào bộ nhớ riêng của ứng dụng trước khi xử lý tiếp.
    private void takePicture(ImageCapture imageCapture) {
        final File file = new File(getExternalFilesDir(null), System.currentTimeMillis() + ".jpg");
        ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture.OutputFileOptions.Builder(file).build();
        imageCapture.takePicture(outputFileOptions, Executors.newCachedThreadPool(), new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                runOnUiThread(() -> {
                    saveCapturedImageToGallery(file);
                    uploadCapturedImage(file);
                });
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                runOnUiThread(() -> {
                    Toast.makeText(
                            CameraActivity.this,
                            getString(R.string.camera_capture_error, exception.getMessage()),
                            Toast.LENGTH_SHORT
                    ).show();
                    setLoadingState(false);
                });
            }
        });
    }

    // Phân tích ảnh vừa chụp hoặc ảnh lấy từ thư viện bằng model ONNX cục bộ để nhận kết quả chẩn đoán.
    private void uploadCapturedImage(@NonNull File imageFile) {
        setLoadingState(true);
        uploadLoadingText.setText(R.string.camera_uploading);

        Thread diagnosisThread = new Thread(() -> {
            try {
                DiagnosisResponse diagnosisResponse = OnnxDiagnosisEngine
                        .getInstance(getApplicationContext())
                        .diagnoseImage(imageFile);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    setLoadingState(false);
                    DiagnosisHelper.launchDiagnosisActivity(CameraActivity.this, diagnosisResponse, imageFile.getAbsolutePath());
                });
            } catch (Exception exception) {
                Log.e(TAG, "Unable to diagnose image using local ONNX model", exception);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    setLoadingState(false);
                    String errorMessage = exception.getMessage() == null || exception.getMessage().trim().isEmpty()
                            ? "Không thể chạy model ONNX trên thiết bị này."
                            : exception.getMessage();
                    Toast.makeText(
                            CameraActivity.this,
                            getString(R.string.camera_diagnosis_failed, errorMessage),
                            Toast.LENGTH_LONG
                    ).show();
                });
            }
        });
        diagnosisThread.start();
    }

    // Bật/tắt trạng thái loading và khóa các nút thao tác khi đang xử lý ảnh.
    private void setLoadingState(boolean isLoading) {
        isUploadingDiagnosis = isLoading;
        uploadLoadingOverlay.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        capture.setEnabled(!isLoading);
        toggeFlash.setEnabled(!isLoading);
        historyRedirect.setEnabled(!isLoading);
        flipCamera.setEnabled(!isLoading);
        addFromGallery.setEnabled(!isLoading);
    }

    // Quyết định cách lưu ảnh chụp vào thư viện và xin quyền ghi bộ nhớ trên Android cũ nếu cần.
    private void saveCapturedImageToGallery(@NonNull File imageFile) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                && ContextCompat.checkSelfPermission(this, permission.WRITE_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            pendingGallerySaveFile = imageFile;
            storagePermissionLauncher.launch(permission.WRITE_EXTERNAL_STORAGE);
            return;
        }

        saveCapturedImageToGalleryAsync(imageFile);
    }

    // Lưu ảnh vào thư viện trên luồng nền để không làm đứng giao diện.
    private void saveCapturedImageToGalleryAsync(@NonNull File imageFile) {
        Thread saveToGalleryThread = new Thread(() -> {
            boolean saved = copyCapturedImageToGallery(imageFile);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }

                Toast.makeText(
                        CameraActivity.this,
                        saved ? R.string.camera_saved_to_gallery : R.string.camera_save_gallery_failed,
                        Toast.LENGTH_SHORT
                ).show();
            });
        });
        saveToGalleryThread.start();
    }

    // Chọn cơ chế lưu ảnh vào thư viện tùy theo phiên bản Android.
    private boolean copyCapturedImageToGallery(@NonNull File imageFile) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return saveImageUsingMediaStore(imageFile);
        }
        return saveImageToLegacyGallery(imageFile);
    }

    // Lưu ảnh vào thư viện bằng MediaStore trên Android 10 trở lên.
    private boolean saveImageUsingMediaStore(@NonNull File imageFile) {
        ContentResolver contentResolver = getContentResolver();
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.Images.Media.DISPLAY_NAME, imageFile.getName());
        contentValues.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, APP_GALLERY_DIRECTORY);
        contentValues.put(MediaStore.Images.Media.IS_PENDING, 1);

        Uri imageUri = null;
        try {
            imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
            if (imageUri == null) {
                return false;
            }

            try (InputStream inputStream = new FileInputStream(imageFile);
                 OutputStream outputStream = contentResolver.openOutputStream(imageUri)) {
                if (outputStream == null) {
                    return false;
                }
                copyStream(inputStream, outputStream);
            }

            contentValues.clear();
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0);
            contentResolver.update(imageUri, contentValues, null, null);
            return true;
        } catch (Exception exception) {
            Log.e(TAG, "Unable to save image to MediaStore", exception);
            if (imageUri != null) {
                contentResolver.delete(imageUri, null, null);
            }
            return false;
        }
    }

    // Lưu ảnh vào thư mục Pictures/DrPlant trên Android cũ và quét media để thư viện nhận ảnh mới.
    private boolean saveImageToLegacyGallery(@NonNull File imageFile) {
        File picturesDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File appGalleryDirectory = new File(picturesDirectory, "DrPlant");
        if (!appGalleryDirectory.exists() && !appGalleryDirectory.mkdirs()) {
            return false;
        }

        File destinationFile = new File(appGalleryDirectory, imageFile.getName());
        try (InputStream inputStream = new FileInputStream(imageFile);
             OutputStream outputStream = new FileOutputStream(destinationFile)) {
            copyStream(inputStream, outputStream);
            MediaScannerConnection.scanFile(
                    this,
                    new String[]{destinationFile.getAbsolutePath()},
                    new String[]{"image/jpeg"},
                    null
            );
            return true;
        } catch (Exception exception) {
            Log.e(TAG, "Unable to save image to legacy gallery", exception);
            return false;
        }
    }

    // Sao chép toàn bộ dữ liệu từ input stream sang output stream.
    private void copyStream(@NonNull InputStream inputStream, @NonNull OutputStream outputStream) throws IOException {
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
        outputStream.flush();
    }

    // Sao chép ảnh được chọn từ thư viện về file tạm trong ứng dụng để thuận tiện upload và preview.
    @NonNull
    private File copyGalleryImageToLocalFile(@NonNull Uri imageUri) throws IOException {
        ContentResolver contentResolver = getContentResolver();
        String extension = resolveImageExtension(contentResolver, imageUri);
        File outputFile = new File(getCacheDir(), "gallery_" + System.currentTimeMillis() + "." + extension);

        try (InputStream inputStream = contentResolver.openInputStream(imageUri);
             FileOutputStream outputStream = new FileOutputStream(outputFile)) {
            if (inputStream == null) {
                throw new IOException("Cannot open selected image stream.");
            }

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.flush();
        }

        return outputFile;
    }

    // Xác định phần mở rộng file ảnh dựa trên MIME type hoặc tên file gốc.
    @NonNull
    private String resolveImageExtension(@NonNull ContentResolver contentResolver, @NonNull Uri imageUri) {
        String mimeType = contentResolver.getType(imageUri);
        if (mimeType != null) {
            String extensionFromMime = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
            if (extensionFromMime != null && !extensionFromMime.trim().isEmpty()) {
                return extensionFromMime;
            }
        }

        String displayName = queryDisplayName(contentResolver, imageUri);
        if (displayName != null) {
            int lastDotIndex = displayName.lastIndexOf('.');
            if (lastDotIndex >= 0 && lastDotIndex < displayName.length() - 1) {
                return displayName.substring(lastDotIndex + 1);
            }
        }

        return "jpg";
    }

    // Lấy tên hiển thị của file ảnh từ Uri mà người dùng đã chọn trong thư viện.
    private String queryDisplayName(@NonNull ContentResolver contentResolver, @NonNull Uri imageUri) {
        try (Cursor cursor = contentResolver.query(imageUri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0) {
                    return cursor.getString(nameIndex);
                }
            }
        } catch (Exception exception) {
            Log.w(TAG, "Unable to query selected image display name", exception);
        }
        return null;
    }
    // Bật hoặc tắt đèn flash và đổi icon theo trạng thái hiện tại của camera.
    private void setFlashIcon(Camera camera) {
        if (camera.getCameraInfo().hasFlashUnit()) {
            Integer torchState = camera.getCameraInfo().getTorchState().getValue();
            if (torchState != null && torchState == 1) {
                camera.getCameraControl().enableTorch(false);
                toggeFlash.setImageResource(R.drawable.ic_flash_off_btn);
            } else {
                camera.getCameraControl().enableTorch(true);
                toggeFlash.setImageResource(R.drawable.ic_flash_on_btn);
            }
        }
        else{
            runOnUiThread(() -> Toast.makeText(CameraActivity.this, R.string.camera_flash_unavailable, Toast.LENGTH_SHORT).show());
        }
    }
}