package com.nckh.drplant;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.nckh.drplant.Models.DiagnosisResponse;
import com.nckh.drplant.Utils.DiagnosisHistoryManager;

import java.io.File;
import java.io.Serializable;
import java.util.Locale;

public class DiagnosisActivity extends AppCompatActivity {

    public static final String EXTRA_DIAGNOSIS_DATA = "diagnosis_data";
    public static final String EXTRA_CAPTURED_IMAGE_PATH = "captured_image_path";
    public static final String EXTRA_SAVE_TO_HISTORY = "save_to_history";
    public static final String EXTRA_OPENED_FROM_HISTORY = "opened_from_history";

    private static final String TAG = "DiagnosisActivity";
    private static final String DEFAULT_ACCENT_COLOR = "#ef4444";
    private static final int IMAGE_PREVIEW_REQUIRED_WIDTH = 1080;
    private static final int IMAGE_PREVIEW_REQUIRED_HEIGHT = 1080;

    private DiagnosisResponse diagnosisData;
    private String capturedImagePath;
    private LinearLayout emptyStateLayout;
    private LinearLayout contentLayout;
    private LinearLayout imagePreviewCard;
    private LinearLayout predictionCard;
    private LinearLayout diseaseCard;
    private LinearLayout severityLayout;
    private LinearLayout solutionsCard;
    private LinearLayout chemicalLayout;
    private LinearLayout biologicalLayout;
    private TextView emptyTitleText;
    private TextView emptyMessageText;
    private TextView screenTitleText;
    private TextView messageText;
    private TextView resultReadyText;
    private ImageView capturedImageView;
    private TextView classNameText;
    private TextView plantTypeText;
    private TextView confidenceText;
    private TextView statusText;
    private TextView diseaseNameText;
    private TextView severityIconText;
    private TextView severityText;
    private TextView symptomsText;
    private TextView causesText;
    private TextView chemicalTitleText;
    private TextView chemicalDescriptionText;
    private TextView biologicalTitleText;
    private TextView biologicalDescriptionText;

    // Khởi tạo màn hình kết quả chẩn đoán, ánh xạ view và nạp dữ liệu truyền sang.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_diagnosis);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Button backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> finish());
        Button historyButton = findViewById(R.id.historyButton);
        historyButton.setOnClickListener(v -> startActivity(new android.content.Intent(this, HistoryActivity.class)));

        bindViews();
        bindHeader(getIntent().getBooleanExtra(EXTRA_OPENED_FROM_HISTORY, false), historyButton);

        capturedImagePath = getIntent().getStringExtra(EXTRA_CAPTURED_IMAGE_PATH);
        diagnosisData = extractDiagnosisData();
        maybeSaveHistory(savedInstanceState == null);
        renderContent();
    }

    // Ánh xạ toàn bộ view cần sử dụng trong màn hình chẩn đoán.
    private void bindViews() {
        emptyStateLayout = findViewById(R.id.emptyStateLayout);
        contentLayout = findViewById(R.id.contentLayout);
        imagePreviewCard = findViewById(R.id.imagePreviewCard);
        predictionCard = findViewById(R.id.predictionCard);
        diseaseCard = findViewById(R.id.diseaseCard);
        severityLayout = findViewById(R.id.severityLayout);
        solutionsCard = findViewById(R.id.solutionsCard);
        chemicalLayout = findViewById(R.id.chemicalLayout);
        biologicalLayout = findViewById(R.id.biologicalLayout);

        emptyTitleText = findViewById(R.id.emptyTitleText);
        emptyMessageText = findViewById(R.id.emptyMessageText);
        screenTitleText = findViewById(R.id.screenTitleText);
        messageText = findViewById(R.id.messageText);
        resultReadyText = findViewById(R.id.resultReadyText);
        capturedImageView = findViewById(R.id.capturedImageView);
        classNameText = findViewById(R.id.classNameText);
        plantTypeText = findViewById(R.id.plantTypeText);
        confidenceText = findViewById(R.id.confidenceText);
        statusText = findViewById(R.id.statusText);
        diseaseNameText = findViewById(R.id.diseaseNameText);
        severityIconText = findViewById(R.id.severityIconText);
        severityText = findViewById(R.id.severityText);
        symptomsText = findViewById(R.id.symptomsText);
        causesText = findViewById(R.id.causesText);
        chemicalTitleText = findViewById(R.id.chemicalTitleText);
        chemicalDescriptionText = findViewById(R.id.chemicalDescriptionText);
        biologicalTitleText = findViewById(R.id.biologicalTitleText);
        biologicalDescriptionText = findViewById(R.id.biologicalDescriptionText);
    }

    // Điều chỉnh phần đầu màn hình để phân biệt đang xem kết quả mới hay xem lại chi tiết từ lịch sử.
    private void bindHeader(boolean openedFromHistory, Button historyButton) {
        screenTitleText.setText(openedFromHistory
                ? R.string.diagnosis_history_detail_title
                : R.string.diagnosis_activity_title);
        historyButton.setVisibility(openedFromHistory ? View.GONE : View.VISIBLE);
    }

    // Lấy dữ liệu chẩn đoán từ Intent dưới dạng object đã được dựng sẵn từ model local.
    @Nullable
    private DiagnosisResponse extractDiagnosisData() {
        Serializable serializable = getIntent().getSerializableExtra(EXTRA_DIAGNOSIS_DATA);
        if (serializable instanceof DiagnosisResponse) {
            return (DiagnosisResponse) serializable;
        }

        return null;
    }

    // Hiển thị trạng thái phù hợp: dữ liệu rỗng hoặc nội dung chẩn đoán đầy đủ.
    private void renderContent() {
        if (diagnosisData == null) {
            showEmptyState(
                    getString(R.string.diagnosis_empty_title),
                    getString(R.string.diagnosis_empty_message)
            );
            return;
        }

        showContentState();
        bindMessageSection();
        bindCapturedImageSection();
        bindPredictionSection();
        bindDiseaseSection();
        bindSolutionsSection();
    }

    // Lưu kết quả hiện tại vào lịch sử nếu đây là lần mở đầu tiên và được phép lưu.
    private void maybeSaveHistory(boolean firstCreation) {
        if (!firstCreation || diagnosisData == null) {
            return;
        }

        boolean saveToHistory = getIntent().getBooleanExtra(EXTRA_SAVE_TO_HISTORY, true);
        if (saveToHistory) {
            DiagnosisHistoryManager.saveHistoryItem(this, diagnosisData, capturedImagePath);
        }
    }

    // Hiển thị giao diện rỗng khi không có dữ liệu chẩn đoán hợp lệ.
    private void showEmptyState(String title, String message) {
        emptyStateLayout.setVisibility(View.VISIBLE);
        contentLayout.setVisibility(View.GONE);
        emptyTitleText.setText(title);
        emptyMessageText.setText(message);
    }

    // Hiển thị nội dung chính của màn hình khi đã có dữ liệu chẩn đoán.
    private void showContentState() {
        emptyStateLayout.setVisibility(View.GONE);
        contentLayout.setVisibility(View.VISIBLE);
    }

    // Gán nội dung cho phần thông báo kết quả ở đầu màn hình.
    private void bindMessageSection() {
        messageText.setText(valueOrDefault(diagnosisData.message, getString(R.string.diagnosis_default_message)));
        resultReadyText.setVisibility(diagnosisData.success ? View.VISIBLE : View.GONE);
    }

    // Hiển thị ảnh đã chụp hoặc ảnh đã chọn nếu file ảnh còn tồn tại và đọc được.
    private void bindCapturedImageSection() {
        if (TextUtils.isEmpty(capturedImagePath)) {
            imagePreviewCard.setVisibility(View.GONE);
            return;
        }

        File imageFile = new File(capturedImagePath);
        if (!imageFile.exists() || !imageFile.isFile()) {
            imagePreviewCard.setVisibility(View.GONE);
            return;
        }

        Bitmap bitmap = decodeSampledBitmap(imageFile.getAbsolutePath(), IMAGE_PREVIEW_REQUIRED_WIDTH, IMAGE_PREVIEW_REQUIRED_HEIGHT);
        if (bitmap == null) {
            imagePreviewCard.setVisibility(View.GONE);
            return;
        }

        imagePreviewCard.setVisibility(View.VISIBLE);
        capturedImageView.setImageBitmap(bitmap);
    }

    // Gán dữ liệu cho phần thông tin dự đoán như tên bệnh, loại cây và độ tin cậy.
    private void bindPredictionSection() {
        DiagnosisResponse.Prediction prediction = diagnosisData.prediction;
        predictionCard.setVisibility(prediction == null ? View.GONE : View.VISIBLE);
        if (prediction == null) {
            return;
        }

        classNameText.setText(formatDisplayName(prediction.className));
        plantTypeText.setText(formatDisplayName(prediction.plantType));
        confidenceText.setText(String.format(Locale.getDefault(), getString(R.string.diagnosis_confidence_format), prediction.confidence));
        statusText.setText(getString(prediction.isHealthy ? R.string.diagnosis_status_healthy : R.string.diagnosis_status_unhealthy));
    }

    // Gán dữ liệu cho phần chi tiết bệnh gồm tên bệnh, triệu chứng và nguyên nhân.
    private void bindDiseaseSection() {
        DiagnosisResponse.Disease disease = diagnosisData.disease;
        diseaseCard.setVisibility(disease == null ? View.GONE : View.VISIBLE);
        if (disease == null) {
            return;
        }

        diseaseNameText.setText(valueOrDefault(disease.name, getString(R.string.diagnosis_not_available)));
        symptomsText.setText(valueOrDefault(disease.symptoms, getString(R.string.diagnosis_not_available)));
        causesText.setText(valueOrDefault(disease.causes, getString(R.string.diagnosis_not_available)));
        bindSeverity(disease.severity);
    }

    // Hiển thị mức độ nghiêm trọng của bệnh và đổi màu theo dữ liệu trả về.
    private void bindSeverity(@Nullable DiagnosisResponse.Severity severity) {
        severityLayout.setVisibility(severity == null ? View.GONE : View.VISIBLE);
        if (severity == null) {
            return;
        }

        severityIconText.setText(valueOrDefault(severity.icon, "⚠️"));
        severityText.setText(String.format(
                Locale.getDefault(),
                getString(R.string.diagnosis_severity_format),
                valueOrDefault(severity.label, getString(R.string.diagnosis_not_available)),
                valueOrDefault(severity.level, getString(R.string.diagnosis_not_available))
        ));
        severityText.setTextColor(parseColorOrDefault(valueOrDefault(severity.color, DEFAULT_ACCENT_COLOR), DEFAULT_ACCENT_COLOR));
    }

    // Gán dữ liệu cho phần giải pháp điều trị hóa học và sinh học.
    private void bindSolutionsSection() {
        DiagnosisResponse.Solutions solutions = diagnosisData.solutions;
        solutionsCard.setVisibility(solutions == null ? View.GONE : View.VISIBLE);
        if (solutions == null) {
            return;
        }

        bindSolutionBlock(chemicalLayout, chemicalTitleText, chemicalDescriptionText, solutions.chemical);
        bindSolutionBlock(biologicalLayout, biologicalTitleText, biologicalDescriptionText, solutions.biological);
    }

    // Hiển thị một khối giải pháp cụ thể và ẩn khối đó nếu dữ liệu không tồn tại.
    private void bindSolutionBlock(LinearLayout layout, TextView titleView, TextView descriptionView, @Nullable DiagnosisResponse.Solution solution) {
        layout.setVisibility(solution == null ? View.GONE : View.VISIBLE);
        if (solution == null) {
            return;
        }

        String title = valueOrDefault(solution.title, getString(R.string.diagnosis_not_available));
        if (!TextUtils.isEmpty(solution.icon)) {
            title = solution.icon + " " + title;
        }
        titleView.setText(title);
        descriptionView.setText(valueOrDefault(solution.description, getString(R.string.diagnosis_not_available)));
    }

    // Chuẩn hóa chuỗi hiển thị bằng cách thay dấu gạch dưới thành khoảng trắng.
    private String formatDisplayName(@Nullable String rawName) {
        if (TextUtils.isEmpty(rawName)) {
            return getString(R.string.diagnosis_not_available);
        }
        return rawName.replace("_", " ");
    }

    // Trả về giá trị mặc định nếu chuỗi đầu vào rỗng hoặc null.
    private String valueOrDefault(@Nullable String value, String fallback) {
        return TextUtils.isEmpty(value) ? fallback : value;
    }

    // Giải mã ảnh từ file với tỉ lệ thu nhỏ phù hợp để tránh tốn bộ nhớ.
    @Nullable
    private Bitmap decodeSampledBitmap(String imagePath, int requiredWidth, int requiredHeight) {
        try {
            BitmapFactory.Options boundsOptions = new BitmapFactory.Options();
            boundsOptions.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(imagePath, boundsOptions);

            BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();
            bitmapOptions.inSampleSize = calculateInSampleSize(boundsOptions, requiredWidth, requiredHeight);
            bitmapOptions.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap decodedBitmap = BitmapFactory.decodeFile(imagePath, bitmapOptions);
            if (decodedBitmap == null) {
                return null;
            }

            return applyExifOrientation(imagePath, decodedBitmap);
        } catch (Exception exception) {
            Log.e(TAG, "Unable to decode captured image", exception);
            return null;
        }
    }

    // Xoay hoặc lật bitmap theo thông tin EXIF để ảnh hiển thị đúng chiều.
    private Bitmap applyExifOrientation(String imagePath, Bitmap bitmap) {
        try {
            ExifInterface exifInterface = new ExifInterface(imagePath);
            int orientation = exifInterface.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
            );

            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    matrix.postRotate(90f);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    matrix.postRotate(180f);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    matrix.postRotate(270f);
                    break;
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                    matrix.preScale(-1f, 1f);
                    break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                    matrix.preScale(1f, -1f);
                    break;
                case ExifInterface.ORIENTATION_TRANSPOSE:
                    matrix.preScale(-1f, 1f);
                    matrix.postRotate(270f);
                    break;
                case ExifInterface.ORIENTATION_TRANSVERSE:
                    matrix.preScale(-1f, 1f);
                    matrix.postRotate(90f);
                    break;
                case ExifInterface.ORIENTATION_NORMAL:
                case ExifInterface.ORIENTATION_UNDEFINED:
                default:
                    return bitmap;
            }

            Bitmap rotatedBitmap = Bitmap.createBitmap(
                    bitmap,
                    0,
                    0,
                    bitmap.getWidth(),
                    bitmap.getHeight(),
                    matrix,
                    true
            );

            if (rotatedBitmap != bitmap) {
                bitmap.recycle();
            }
            return rotatedBitmap;
        } catch (Exception exception) {
            Log.e(TAG, "Unable to apply EXIF orientation", exception);
            return bitmap;
        }
    }

    // Tính hệ số thu nhỏ ảnh phù hợp với kích thước preview mong muốn.
    private int calculateInSampleSize(BitmapFactory.Options options, int requiredWidth, int requiredHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;

        if (height > requiredHeight || width > requiredWidth) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= requiredHeight && (halfWidth / inSampleSize) >= requiredWidth) {
                inSampleSize *= 2;
            }
        }

        return Math.max(inSampleSize, 1);
    }

    // Chuyển chuỗi màu sang mã màu Android và dùng màu dự phòng nếu dữ liệu không hợp lệ.
    private int parseColorOrDefault(String colorHex, String fallbackColor) {
        try {
            return Color.parseColor(colorHex);
        } catch (IllegalArgumentException exception) {
            return Color.parseColor(fallbackColor);
        }
    }
}

