package com.example.drplantadmin;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RecordDetailActivity extends Activity {

    private RecordDatabaseHelper databaseHelper;
    private long recordId = -1;

    private LinearLayout contentLayout;
    private DiagnosisRecord selectedRecord;

    private int dp(float value) {
        return (int) (value * getResources()
                .getDisplayMetrics().density + 0.5f);
    }

    private TextView createText(String text, int size) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(size);
        tv.setTextColor(Color.rgb(45, 55, 45));
        tv.setPadding(0, dp(8), 0, dp(8));
        return tv;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        recordId = getIntent().getLongExtra("record_id", -1);
        databaseHelper = new RecordDatabaseHelper(this);

        createInterface();
        loadRecord();
    }

    private void createInterface() {
        ScrollView scrollView = new ScrollView(this);

        contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(dp(20), dp(20), dp(20), dp(20));
        contentLayout.setBackgroundColor(Color.rgb(245, 249, 245));

        scrollView.addView(contentLayout);
        setContentView(scrollView);
    }

    private void loadRecord() {
        List<DiagnosisRecord> records = databaseHelper.getAllRecords();

        selectedRecord = null;

        for (DiagnosisRecord record : records) {
            if (record.id == recordId) {
                selectedRecord = record;
                break;
            }
        }

        if (selectedRecord == null) {
            Toast.makeText(
                    this,
                    "Không tìm thấy bản ghi",
                    Toast.LENGTH_LONG
            ).show();

            finish();
            return;
        }

        displayRecord();
    }

    private void displayRecord() {
        contentLayout.removeAllViews();

        TextView title = createText("Chi tiết bản ghi", 24);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        contentLayout.addView(title);

        addInfo("Mã bản ghi", String.valueOf(selectedRecord.id));
        addInfo("Loại cây", selectedRecord.plantType);

        String statusText;

        if ("HEALTHY".equalsIgnoreCase(selectedRecord.status)
                || "Khỏe mạnh".equalsIgnoreCase(selectedRecord.status)) {
            statusText = "Khỏe mạnh";
        } else {
            statusText = "Có bệnh";
        }

        addInfo("Trạng thái", statusText);
        addInfo("Kết quả chẩn đoán", selectedRecord.diseaseName);

        SimpleDateFormat dateFormat = new SimpleDateFormat(
                "dd/MM/yyyy HH:mm:ss",
                Locale.getDefault()
        );

        addInfo(
                "Thời gian chẩn đoán",
                dateFormat.format(new Date(selectedRecord.diagnosedAt))
        );

        // Tiêu đề ảnh
        TextView imageTitle = createText("Hình ảnh chẩn đoán", 18);
        imageTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        contentLayout.addView(imageTitle);

        // Khung hiển thị ảnh
        ImageView imageView = new ImageView(this);
        imageView.setAdjustViewBounds(true);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setBackgroundColor(Color.rgb(230, 235, 228));

        LinearLayout.LayoutParams imageParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(280)
                );

        imageParams.bottomMargin = dp(12);
        contentLayout.addView(imageView, imageParams);

        loadImageIntoView(imageView, selectedRecord.imagePath);

        addInfo("Hướng dẫn chăm sóc", selectedRecord.careAdvice);

        Button btnEdit = new Button(this);
        btnEdit.setText("Chỉnh sửa bản ghi");
        btnEdit.setAllCaps(false);
        contentLayout.addView(btnEdit);

        btnEdit.setOnClickListener(v -> {
            Intent intent = new Intent(
                    RecordDetailActivity.this,
                    EditRecordActivity.class
            );

            intent.putExtra("record_id", selectedRecord.id);
            startActivity(intent);
        });

        Button btnBack = new Button(this);
        btnBack.setText("Quay lại danh sách");
        btnBack.setAllCaps(false);
        contentLayout.addView(btnBack);

        btnBack.setOnClickListener(v -> finish());
    }

    private void loadImageIntoView(ImageView imageView, String imagePath) {
        imageView.setImageDrawable(null);

        if (imagePath == null || imagePath.trim().isEmpty()) {
            showImagePlaceholder(imageView, "Chưa có hình ảnh");
            return;
        }

        try {
            Uri imageUri = Uri.parse(imagePath);

            if ("content".equalsIgnoreCase(imageUri.getScheme())) {
                imageView.setImageURI(imageUri);

                if (imageView.getDrawable() == null) {
                    showImagePlaceholder(
                            imageView,
                            "Không thể hiển thị ảnh"
                    );
                }
            } else {
                Bitmap bitmap = BitmapFactory.decodeFile(imagePath);

                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap);
                } else {
                    showImagePlaceholder(
                            imageView,
                            "Không đọc được tệp ảnh"
                    );
                }
            }
        } catch (Exception e) {
            android.util.Log.e(
                    "RECORD_DETAIL",
                    "Lỗi hiển thị ảnh: " + imagePath,
                    e
            );

            showImagePlaceholder(imageView, "Không thể hiển thị ảnh");
        }
    }

    private void showImagePlaceholder(ImageView imageView, String message) {
        imageView.setImageResource(android.R.drawable.ic_menu_gallery);
        imageView.setContentDescription(message);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void addInfo(String label, String value) {
        TextView tv = createText(
                label + ": "
                        + (value == null || value.trim().isEmpty()
                        ? "Chưa có thông tin" : value),
                16
        );

        contentLayout.addView(tv);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (databaseHelper != null && contentLayout != null) {
            loadRecord();
        }
    }
}
