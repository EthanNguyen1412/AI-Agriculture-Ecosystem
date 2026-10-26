package com.example.drplantadmin;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class AddRecordActivity extends Activity {

    private EditText edtPlant, edtDisease, edtAdvice;
    private Spinner spnStatus;
    private RecordDatabaseHelper databaseHelper;

    private ImageView imgPreview;
    private Uri selectedImageUri = null;

    private static final int PICK_IMAGE_REQUEST = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        databaseHelper = new RecordDatabaseHelper(this);

        int padding = dp(22);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.rgb(244, 247, 242));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(padding, padding, padding, padding);
        scrollView.addView(root);

        TextView heading = new TextView(this);
        heading.setText("Thêm bản ghi");
        heading.setTextSize(27);
        heading.setTextColor(Color.rgb(24, 59, 42));
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(heading);

        TextView subtitle = new TextView(this);
        subtitle.setText("Nhập thông tin chẩn đoán cây trồng");
        subtitle.setTextSize(14);
        subtitle.setTextColor(Color.GRAY);
        subtitle.setPadding(0, dp(8), 0, dp(22));
        root.addView(subtitle);

        edtPlant = addEditText(root, "Loại cây, ví dụ: Sầu riêng");

        TextView statusLabel = makeLabel("Tình trạng");
        root.addView(statusLabel);

        spnStatus = new Spinner(this);

        String[] statuses = {
                "Khỏe mạnh",
                "Có bệnh"
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                statuses
        );

        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spnStatus.setAdapter(adapter);

        root.addView(spnStatus, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52)
        ));

        edtDisease = addEditText(
                root,
                "Tên bệnh hoặc kết quả chẩn đoán"
        );

        edtAdvice = addEditText(
                root,
                "Gợi ý chăm sóc đầy đủ"
        );

        edtAdvice.setMinLines(4);
        edtAdvice.setGravity(Gravity.TOP | Gravity.START);

        // Tiêu đề hình ảnh
        TextView imageLabel = makeLabel("Hình ảnh chẩn đoán");
        root.addView(imageLabel);

        // Khung xem trước ảnh
        imgPreview = new ImageView(this);
        imgPreview.setImageResource(android.R.drawable.ic_menu_gallery);
        imgPreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imgPreview.setAdjustViewBounds(true);
        imgPreview.setBackgroundColor(Color.rgb(230, 235, 228));

        LinearLayout.LayoutParams imageParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(220)
                );

        imageParams.bottomMargin = dp(10);
        root.addView(imgPreview, imageParams);

        // Nút chọn ảnh
        Button btnChooseImage = new Button(this);
        btnChooseImage.setText("Chọn ảnh từ thư viện");
        btnChooseImage.setAllCaps(false);
        root.addView(btnChooseImage);

        btnChooseImage.setOnClickListener(v -> chooseImage());

        // Nút lưu
        Button btnSave = new Button(this);
        btnSave.setText("Lưu bản ghi");
        btnSave.setTextColor(Color.WHITE);
        btnSave.setTextSize(16);
        btnSave.setAllCaps(false);

        btnSave.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        Color.rgb(38, 115, 74)
                )
        );

        LinearLayout.LayoutParams saveParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(54)
                );

        saveParams.topMargin = dp(24);
        root.addView(btnSave, saveParams);

        // Nút hủy
        Button btnCancel = new Button(this);
        btnCancel.setText("Hủy");
        btnCancel.setAllCaps(false);
        root.addView(btnCancel);

        btnCancel.setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> saveRecord());

        setContentView(scrollView);
    }

    private void chooseImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");

        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST
                && resultCode == RESULT_OK
                && data != null
                && data.getData() != null) {

            selectedImageUri = data.getData();

            try {
                getContentResolver().takePersistableUriPermission(
                        selectedImageUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                );
            } catch (Exception e) {
                android.util.Log.e(
                        "ADD_RECORD",
                        "Không lưu được quyền đọc ảnh",
                        e
                );
            }

            imgPreview.setImageURI(null);
            imgPreview.setImageURI(selectedImageUri);
        }
    }

    private EditText addEditText(
            LinearLayout parent,
            String hint
    ) {
        TextView label = makeLabel(hint);
        parent.addView(label);

        EditText editText = new EditText(this);
        editText.setSingleLine(false);
        editText.setTextSize(15);
        editText.setHint(hint);
        editText.setPadding(dp(12), dp(8), dp(12), dp(8));

        editText.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        Color.rgb(57, 132, 91)
                )
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        params.bottomMargin = dp(15);
        parent.addView(editText, params);

        return editText;
    }

    private TextView makeLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(Color.rgb(45, 67, 51));
        label.setTextSize(14);
        label.setPadding(0, dp(8), 0, dp(4));
        return label;
    }

    private void saveRecord() {
        String plant = edtPlant.getText().toString().trim();
        String disease = edtDisease.getText().toString().trim();
        String advice = edtAdvice.getText().toString().trim();

        if (plant.isEmpty()) {
            edtPlant.setError("Vui lòng nhập loại cây");
            edtPlant.requestFocus();
            return;
        }

        if (disease.isEmpty()) {
            edtDisease.setError("Vui lòng nhập kết quả");
            edtDisease.requestFocus();
            return;
        }

        if (advice.isEmpty()) {
            edtAdvice.setError("Vui lòng nhập hướng dẫn chăm sóc");
            edtAdvice.requestFocus();
            return;
        }

        if (selectedImageUri == null) {
            Toast.makeText(
                    this,
                    "Vui lòng chọn hình ảnh chẩn đoán",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        String status = spnStatus.getSelectedItemPosition() == 0
                ? "HEALTHY"
                : "DISEASE";

        // Lưu URI của ảnh vào cơ sở dữ liệu
        String imagePath = selectedImageUri.toString();

        long result = databaseHelper.addRecord(
                plant,
                status,
                disease,
                imagePath,
                advice
        );

        if (result != -1) {
            Toast.makeText(
                    this,
                    "Đã lưu bản ghi thành công",
                    Toast.LENGTH_SHORT
            ).show();

            finish();
        } else {
            Toast.makeText(
                    this,
                    "Không thể lưu bản ghi",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private int dp(float value) {
        return (int) (value * getResources()
                .getDisplayMetrics().density + 0.5f);
    }
}
