package com.example.drplantadmin;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

public class EditRecordActivity extends Activity {

    private RecordDatabaseHelper databaseHelper;
    private long recordId;

    private EditText edtPlantType;
    private EditText edtDiseaseName;
    private EditText edtCareAdvice;
    private Spinner spnStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        databaseHelper = new RecordDatabaseHelper(this);
        recordId = getIntent().getLongExtra("record_id", -1);

        createInterface();
        loadRecord();
    }

    private void createInterface() {
        ScrollView scrollView = new ScrollView(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(36, 32, 36, 32);
        root.setBackgroundColor(Color.rgb(244, 247, 242));

        TextView title = new TextView(this);
        title.setText("Chỉnh sửa bản ghi");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(36, 92, 58));
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title);

        edtPlantType = createInput(root, "Loại cây");
        addLabel(root, "Tình trạng cây");

        spnStatus = new Spinner(this);

        String[] statuses = {"Khỏe mạnh", "Bệnh"};

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                statuses
        );

        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spnStatus.setAdapter(adapter);
        root.addView(spnStatus);

        edtDiseaseName = createInput(root, "Tên bệnh");
        edtCareAdvice = createInput(root, "Hướng dẫn chăm sóc");
        edtCareAdvice.setMinLines(3);
        edtCareAdvice.setGravity(Gravity.TOP);

        Button btnSave = new Button(this);
        btnSave.setText("Lưu thay đổi");
        root.addView(btnSave);

        Button btnCancel = new Button(this);
        btnCancel.setText("Hủy");
        root.addView(btnCancel);

        btnSave.setOnClickListener(v -> saveChanges());
        btnCancel.setOnClickListener(v -> finish());

        scrollView.addView(root);
        setContentView(scrollView);
    }

    private void addLabel(LinearLayout root, String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(15);
        label.setTextColor(Color.DKGRAY);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 18, 0, 6);

        root.addView(label, params);
    }

    private EditText createInput(
            LinearLayout root,
            String label
    ) {
        addLabel(root, label);

        EditText input = new EditText(this);
        input.setTextSize(16);
        input.setPadding(18, 10, 18, 10);
        input.setBackgroundColor(Color.WHITE);

        root.addView(
                input,
                new LinearLayout.LayoutParams(-1, -2)
        );

        return input;
    }

    private DiagnosisRecord findRecord(long id) {
        List<DiagnosisRecord> records =
                databaseHelper.getAllRecords();

        for (DiagnosisRecord record : records) {
            if (record.id == id) {
                return record;
            }
        }

        return null;
    }

    private void loadRecord() {
        DiagnosisRecord record = findRecord(recordId);

        if (record == null) {
            Toast.makeText(
                    this,
                    "Không tìm thấy bản ghi",
                    Toast.LENGTH_SHORT
            ).show();
            finish();
            return;
        }

        edtPlantType.setText(record.plantType);
        edtDiseaseName.setText(record.diseaseName);
        edtCareAdvice.setText(record.careAdvice);

        int statusPosition =
                "HEALTHY".equalsIgnoreCase(record.status) ? 0 : 1;

        spnStatus.setSelection(statusPosition);
    }

    private void saveChanges() {
        String plantType =
                edtPlantType.getText().toString().trim();

        String status = spnStatus.getSelectedItemPosition() == 0
                ? "HEALTHY"
                : "DISEASE";

        String diseaseName =
                edtDiseaseName.getText().toString().trim();

        String careAdvice =
                edtCareAdvice.getText().toString().trim();

        if (plantType.isEmpty()) {
            edtPlantType.setError("Vui lòng nhập loại cây");
            return;
        }

        if ("DISEASE".equals(status) && diseaseName.isEmpty()) {
            edtDiseaseName.setError("Vui lòng nhập tên bệnh");
            return;
        }
        if (status.equals("Khỏe mạnh")) {
            diseaseName = "Không có";
        }

        if (careAdvice.isEmpty()) {
            edtCareAdvice.setError(
                    "Vui lòng nhập hướng dẫn chăm sóc"
            );
            return;
        }

        int result = databaseHelper.updateRecord(
                recordId,
                plantType,
                status,
                diseaseName,
                careAdvice
        );

        if (result > 0) {
            Toast.makeText(
                    this,
                    "Đã cập nhật bản ghi",
                    Toast.LENGTH_SHORT
            ).show();
            finish();
        } else {
            Toast.makeText(
                    this,
                    "Cập nhật thất bại",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }
}
