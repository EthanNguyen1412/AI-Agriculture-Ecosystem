
package com.example.drplantadmin;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class RecordListActivity extends Activity {

    private RecordDatabaseHelper databaseHelper;

    private LinearLayout recordContainer;
    private TextView txtRecordCount;
    private TextView txtDateRange;
    private EditText edtSearch;

    private Spinner spnPlantFilter;
    private Spinner spnStatusFilter;
    private Spinner spnDiseaseFilter;

    private Button btnAddRecord;
    private Button btnStartDate;
    private Button btnEndDate;
    private Button btnFilterDate;
    private Button btnClearFilters;

    private long startDateMillis = -1;
    private long endDateMillis = -1;

    private final Set<Long> selectedRecordIds = new HashSet<>();
    private final List<DiagnosisRecord> allRecords = new ArrayList<>();
    private final List<DiagnosisRecord> filteredRecords = new ArrayList<>();

    private boolean selectionMode = false;

    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

    private final SimpleDateFormat dayFormat =
            new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record_list);

        databaseHelper = new RecordDatabaseHelper(this);

        initializeViews();
        setupSpinners();
        setupListeners();
        loadRecords();
        updateDateRangeText();
    }

    private void initializeViews() {
        recordContainer = findViewById(R.id.recordContainer);
        txtRecordCount = findViewById(R.id.txtRecordCount);
        txtDateRange = findViewById(R.id.txtDateRange);
        edtSearch = findViewById(R.id.edtSearch);

        spnPlantFilter = findViewById(R.id.spnPlantFilter);
        spnStatusFilter = findViewById(R.id.spnStatusFilter);
        spnDiseaseFilter = findViewById(R.id.spnDiseaseFilter);

        btnAddRecord = findViewById(R.id.btnAddRecord);
        btnStartDate = findViewById(R.id.btnStartDate);
        btnEndDate = findViewById(R.id.btnEndDate);
        btnFilterDate = findViewById(R.id.btnFilterDate);
        btnClearFilters = findViewById(R.id.btnClearFilters);
    }

    private void setupSpinners() {
        String[] plantOptions = {
                "Tất cả loại cây",
                "Sầu riêng",
                "Cà phê"
        };

        ArrayAdapter<String> plantAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                plantOptions
        );
        plantAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );
        spnPlantFilter.setAdapter(plantAdapter);

        String[] statusOptions = {
                "Tất cả trạng thái",
                "Khỏe mạnh",
                "Có bệnh"
        };

        ArrayAdapter<String> statusAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                statusOptions
        );
        statusAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );
        spnStatusFilter.setAdapter(statusAdapter);

        List<String> diseaseOptions = new ArrayList<>();
        diseaseOptions.add("Tất cả loại bệnh");

        ArrayAdapter<String> diseaseAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                diseaseOptions
        );
        diseaseAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );
        spnDiseaseFilter.setAdapter(diseaseAdapter);
    }

    private void refreshDiseaseOptions() {
        if (spnDiseaseFilter == null || databaseHelper == null) {
            return;
        }

        String previousSelection = spnDiseaseFilter.getSelectedItem() == null
                ? "Tất cả loại bệnh"
                : spnDiseaseFilter.getSelectedItem().toString();

        List<String> diseaseOptions = new ArrayList<>();
        diseaseOptions.add("Tất cả loại bệnh");

        List<DiagnosisRecord> records = databaseHelper.getAllRecords();

        for (DiagnosisRecord record : records) {
            if (record == null || isHealthy(record.status)) {
                continue;
            }

            String diseaseName = record.diseaseName;

            if (diseaseName == null || diseaseName.trim().isEmpty()) {
                continue;
            }

            diseaseName = diseaseName.trim();

            boolean exists = false;
            for (String existing : diseaseOptions) {
                if (existing.equalsIgnoreCase(diseaseName)) {
                    exists = true;
                    break;
                }
            }

            if (!exists) {
                diseaseOptions.add(diseaseName);
            }
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                diseaseOptions
        );
        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spnDiseaseFilter.setAdapter(adapter);

        int selectedPosition = 0;
        for (int i = 0; i < diseaseOptions.size(); i++) {
            if (diseaseOptions.get(i).equalsIgnoreCase(previousSelection)) {
                selectedPosition = i;
                break;
            }
        }

        spnDiseaseFilter.setSelection(selectedPosition);
    }

    private void setupListeners() {
        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(
                    CharSequence s, int start, int before, int count) {
                filterRecords();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        AdapterView.OnItemSelectedListener filterListener =
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            View view,
                            int position,
                            long id) {
                        filterRecords();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                };

        spnPlantFilter.setOnItemSelectedListener(filterListener);
        spnStatusFilter.setOnItemSelectedListener(filterListener);
        spnDiseaseFilter.setOnItemSelectedListener(filterListener);

        btnStartDate.setOnClickListener(v -> showDatePicker(true));
        btnEndDate.setOnClickListener(v -> showDatePicker(false));

        btnFilterDate.setOnClickListener(v -> {
            if (startDateMillis != -1
                    && endDateMillis != -1
                    && startDateMillis > endDateMillis) {
                Toast.makeText(
                        this,
                        "Ngày bắt đầu không được sau ngày kết thúc",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            updateDateRangeText();
            filterRecords();
        });

        btnClearFilters.setOnClickListener(v -> clearFilters());

        // Mở camera AI thay cho form nhập bản ghi thủ công.
        btnAddRecord.setOnClickListener(v -> {
            Intent intent = new Intent(
                    RecordListActivity.this,
                    AiCameraActivity.class
            );
            startActivity(intent);
        });
    }

    private void loadRecords() {
        if (databaseHelper == null) {
            return;
        }

        allRecords.clear();
        allRecords.addAll(databaseHelper.getAllRecords());

        Set<Long> existingIds = new HashSet<>();
        for (DiagnosisRecord record : allRecords) {
            existingIds.add(record.id);
        }

        selectedRecordIds.retainAll(existingIds);

        if (selectedRecordIds.isEmpty()) {
            selectionMode = false;
        }

        refreshDiseaseOptions();
        filterRecords();
    }

    private void filterRecords() {
        if (recordContainer == null
                || edtSearch == null
                || spnPlantFilter == null
                || spnStatusFilter == null
                || spnDiseaseFilter == null) {
            return;
        }

        filteredRecords.clear();

        String keyword = edtSearch.getText()
                .toString()
                .trim()
                .toLowerCase(Locale.ROOT);

        String selectedPlant = getSelectedSpinnerValue(
                spnPlantFilter, "Tất cả loại cây"
        );

        String selectedStatus = getSelectedSpinnerValue(
                spnStatusFilter, "Tất cả trạng thái"
        );

        String selectedDisease = getSelectedSpinnerValue(
                spnDiseaseFilter, "Tất cả loại bệnh"
        );

        for (DiagnosisRecord record : allRecords) {
            if (record == null) {
                continue;
            }

            String searchableText =
                    safe(record.id) + " "
                            + safe(record.plantType) + " "
                            + safe(record.status) + " "
                            + safe(record.diseaseName) + " "
                            + safe(record.careAdvice);

            if (!keyword.isEmpty()
                    && !searchableText.toLowerCase(Locale.ROOT)
                    .contains(keyword)) {
                continue;
            }

            if ("Sầu riêng".equals(selectedPlant)
                    && !isDurian(record.plantType)) {
                continue;
            }

            if ("Cà phê".equals(selectedPlant)
                    && !isCoffee(record.plantType)) {
                continue;
            }

            if ("Khỏe mạnh".equals(selectedStatus)
                    && !isHealthy(record.status)) {
                continue;
            }

            if ("Có bệnh".equals(selectedStatus)
                    && isHealthy(record.status)) {
                continue;
            }

            if (!"Tất cả loại bệnh".equals(selectedDisease)) {
                if (isHealthy(record.status)
                        || record.diseaseName == null
                        || !record.diseaseName.trim()
                        .equalsIgnoreCase(selectedDisease)) {
                    continue;
                }
            }

            if (startDateMillis != -1
                    && record.diagnosedAt < startDateMillis) {
                continue;
            }

            if (endDateMillis != -1
                    && record.diagnosedAt > endDateMillis) {
                continue;
            }

            filteredRecords.add(record);
        }

        renderRecords();
    }

    private String getSelectedSpinnerValue(
            Spinner spinner, String defaultValue) {
        if (spinner == null || spinner.getSelectedItem() == null) {
            return defaultValue;
        }

        return spinner.getSelectedItem().toString();
    }

    private void renderRecords() {
        recordContainer.removeAllViews();
        addSelectionToolbar();

        txtRecordCount.setText(
                "Tìm thấy " + filteredRecords.size() + " bản ghi"
        );

        if (filteredRecords.isEmpty()) {
            TextView emptyView = createText(
                    "Không tìm thấy bản ghi phù hợp.", 16
            );
            emptyView.setGravity(Gravity.CENTER);
            emptyView.setPadding(dp(16), dp(32), dp(16), dp(32));
            recordContainer.addView(emptyView);
            return;
        }

        for (DiagnosisRecord record : filteredRecords) {
            recordContainer.addView(createRecordCard(record));
        }
    }

    private void addSelectionToolbar() {
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.VERTICAL);
        toolbar.setPadding(dp(8), dp(8), dp(8), dp(8));

        TextView selectionText = createText(
                "Đã chọn " + selectedRecordIds.size() + " bản ghi", 14
        );
        toolbar.addView(selectionText);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);

        Button selectAllButton = new Button(this);
        selectAllButton.setText("Chọn tất cả");
        selectAllButton.setTextSize(12);

        selectAllButton.setOnClickListener(v -> {
            boolean allSelected = !filteredRecords.isEmpty();

            for (DiagnosisRecord record : filteredRecords) {
                if (!selectedRecordIds.contains(record.id)) {
                    allSelected = false;
                    break;
                }
            }

            if (allSelected) {
                for (DiagnosisRecord record : filteredRecords) {
                    selectedRecordIds.remove(record.id);
                }
            } else {
                for (DiagnosisRecord record : filteredRecords) {
                    selectedRecordIds.add(record.id);
                }
            }

            selectionMode = !selectedRecordIds.isEmpty();
            renderRecords();
        });

        Button deleteButton = new Button(this);
        deleteButton.setText("Xóa đã chọn");
        deleteButton.setTextSize(12);
        deleteButton.setOnClickListener(v -> confirmDeleteSelected());

        Button cancelButton = new Button(this);
        cancelButton.setText("Bỏ chọn");
        cancelButton.setTextSize(12);
        cancelButton.setOnClickListener(v -> {
            selectedRecordIds.clear();
            selectionMode = false;
            renderRecords();
        });

        actions.addView(selectAllButton);
        actions.addView(deleteButton);
        actions.addView(cancelButton);

        toolbar.addView(actions);
        recordContainer.addView(toolbar);
    }

    private View createRecordCard(DiagnosisRecord record) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(14));
        background.setStroke(dp(1), Color.rgb(225, 232, 225));
        card.setBackground(background);

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
        cardParams.setMargins(dp(8), dp(5), dp(8), dp(8));
        card.setLayoutParams(cardParams);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        CheckBox checkBox = new CheckBox(this);
        checkBox.setChecked(selectedRecordIds.contains(record.id));

        checkBox.setOnClickListener(v -> {
            if (checkBox.isChecked()) {
                selectedRecordIds.add(record.id);
            } else {
                selectedRecordIds.remove(record.id);
            }

            selectionMode = !selectedRecordIds.isEmpty();
            renderRecords();
        });

        row.addView(checkBox);

        ImageView thumbnail = new ImageView(this);
        thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);

        LinearLayout.LayoutParams imageParams =
                new LinearLayout.LayoutParams(dp(76), dp(76));
        imageParams.setMargins(dp(4), 0, dp(12), 0);
        thumbnail.setLayoutParams(imageParams);

        GradientDrawable imageBackground = new GradientDrawable();
        imageBackground.setColor(Color.rgb(229, 244, 233));
        imageBackground.setCornerRadius(dp(10));
        thumbnail.setBackground(imageBackground);
        thumbnail.setClipToOutline(true);

        Bitmap bitmap = decodeImage(record.imagePath);

        if (bitmap != null) {
            thumbnail.setImageBitmap(bitmap);
        } else {
            thumbnail.setImageResource(
                    android.R.drawable.ic_menu_gallery
            );
            thumbnail.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        }

        row.addView(thumbnail);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams infoParams =
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1
                );
        info.setLayoutParams(infoParams);

        TextView plantText = createText(safe(record.plantType), 16);
        plantText.setTypeface(null, Typeface.BOLD);
        info.addView(plantText);

        TextView diseaseText = createText(
                isHealthy(record.status)
                        ? "Không phát hiện bệnh"
                        : safe(record.diseaseName),
                14
        );
        diseaseText.setPadding(0, dp(4), 0, dp(4));
        info.addView(diseaseText);

        TextView statusText = createText(
                isHealthy(record.status)
                        ? "Trạng thái: Khỏe mạnh"
                        : "Trạng thái: Có bệnh",
                13
        );
        statusText.setTextColor(
                isHealthy(record.status)
                        ? Color.rgb(39, 125, 70)
                        : Color.rgb(190, 65, 55)
        );
        info.addView(statusText);

        TextView timeText = createText(
                formatDate(record.diagnosedAt), 12
        );
        timeText.setTextColor(Color.GRAY);
        timeText.setPadding(0, dp(4), 0, 0);
        info.addView(timeText);

        row.addView(info);
        card.addView(row);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

        Button detailButton = new Button(this);
        detailButton.setText("Xem chi tiết");
        detailButton.setTextSize(12);
        detailButton.setOnClickListener(v -> {
            if (selectionMode) {
                toggleSelection(record.id);
            } else {
                openRecordDetail(record);
            }
        });

        Button deleteOneButton = new Button(this);
        deleteOneButton.setText("Xóa");
        deleteOneButton.setTextSize(12);
        deleteOneButton.setOnClickListener(v -> confirmDeleteOne(record));

        buttons.addView(detailButton);
        buttons.addView(deleteOneButton);
        card.addView(buttons);

        card.setOnClickListener(v -> {
            if (selectionMode) {
                toggleSelection(record.id);
            } else {
                openRecordDetail(record);
            }
        });

        return card;
    }

    private void toggleSelection(long recordId) {
        if (selectedRecordIds.contains(recordId)) {
            selectedRecordIds.remove(recordId);
        } else {
            selectedRecordIds.add(recordId);
        }

        selectionMode = !selectedRecordIds.isEmpty();
        renderRecords();
    }

    private void openRecordDetail(DiagnosisRecord record) {
        Intent intent = new Intent(
                RecordListActivity.this,
                RecordDetailActivity.class
        );
        intent.putExtra("record_id", record.id);
        startActivity(intent);
    }

    private void confirmDeleteOne(DiagnosisRecord record) {
        new AlertDialog.Builder(this)
                .setTitle("Xác nhận xóa")
                .setMessage(
                        "Bạn có chắc muốn xóa bản ghi #"
                                + record.id + "?"
                )
                .setNegativeButton("Hủy", (dialog, which) ->
                        dialog.dismiss())
                .setPositiveButton("Xóa", (dialog, which) -> {
                    databaseHelper.deleteRecord(record.id);
                    selectedRecordIds.remove(record.id);
                    loadRecords();

                    Toast.makeText(
                            this, "Đã xóa bản ghi", Toast.LENGTH_SHORT
                    ).show();
                })
                .show();
    }

    private void confirmDeleteSelected() {
        if (selectedRecordIds.isEmpty()) {
            Toast.makeText(
                    this, "Bạn chưa chọn bản ghi nào",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Xác nhận xóa")
                .setMessage(
                        "Bạn có chắc muốn xóa "
                                + selectedRecordIds.size()
                                + " bản ghi đã chọn?"
                )
                .setNegativeButton("Hủy", (dialog, which) ->
                        dialog.dismiss())
                .setPositiveButton("Xóa", (dialog, which) -> {
                    List<Long> idsToDelete =
                            new ArrayList<>(selectedRecordIds);

                    for (Long id : idsToDelete) {
                        databaseHelper.deleteRecord(id);
                    }

                    selectedRecordIds.clear();
                    selectionMode = false;
                    loadRecords();

                    Toast.makeText(
                            this, "Đã xóa các bản ghi được chọn",
                            Toast.LENGTH_SHORT
                    ).show();
                })
                .show();
    }

    private void showDatePicker(boolean isStartDate) {
        Calendar calendar = Calendar.getInstance();

        long existingDate = isStartDate
                ? startDateMillis
                : endDateMillis;

        if (existingDate != -1) {
            calendar.setTimeInMillis(existingDate);
        }

        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    Calendar selected = Calendar.getInstance();
                    selected.set(
                            year, month, dayOfMonth, 0, 0, 0
                    );
                    selected.set(Calendar.MILLISECOND, 0);

                    if (isStartDate) {
                        startDateMillis = selected.getTimeInMillis();
                        btnStartDate.setText(
                                "Từ: " + dayFormat.format(selected.getTime())
                        );
                    } else {
                        selected.set(Calendar.HOUR_OF_DAY, 23);
                        selected.set(Calendar.MINUTE, 59);
                        selected.set(Calendar.SECOND, 59);
                        selected.set(Calendar.MILLISECOND, 999);

                        endDateMillis = selected.getTimeInMillis();
                        btnEndDate.setText(
                                "Đến: " + dayFormat.format(selected.getTime())
                        );
                    }

                    updateDateRangeText();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );

        dialog.show();
    }

    private void updateDateRangeText() {
        if (txtDateRange == null) {
            return;
        }

        String startText = startDateMillis == -1
                ? "Không giới hạn"
                : dayFormat.format(new Date(startDateMillis));

        String endText = endDateMillis == -1
                ? "Không giới hạn"
                : dayFormat.format(new Date(endDateMillis));

        txtDateRange.setText(startText + " - " + endText);
    }

    private void clearFilters() {
        edtSearch.setText("");

        startDateMillis = -1;
        endDateMillis = -1;

        btnStartDate.setText("Ngày bắt đầu");
        btnEndDate.setText("Ngày kết thúc");

        spnPlantFilter.setSelection(0);
        spnStatusFilter.setSelection(0);
        spnDiseaseFilter.setSelection(0);

        selectedRecordIds.clear();
        selectionMode = false;

        updateDateRangeText();
        filterRecords();
    }

    private Bitmap decodeImage(String imagePath) {
        if (imagePath == null || imagePath.trim().isEmpty()) {
            return null;
        }

        try {
            if (imagePath.startsWith("content://")) {
                return null;
            }

            return BitmapFactory.decodeFile(imagePath);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isHealthy(String status) {
        if (status == null) {
            return false;
        }

        String value = status.trim().toLowerCase(Locale.ROOT);

        return value.equals("healthy")
                || value.equals("khỏe mạnh")
                || value.equals("khoe manh")
                || value.equals("healthy plant");
    }

    private boolean isDurian(String plantType) {
        if (plantType == null) {
            return false;
        }

        String value = plantType.trim().toLowerCase(Locale.ROOT);

        return value.contains("durian")
                || value.contains("sầu riêng")
                || value.contains("sau rieng");
    }

    private boolean isCoffee(String plantType) {
        if (plantType == null) {
            return false;
        }

        String value = plantType.trim().toLowerCase(Locale.ROOT);

        return value.contains("coffee")
                || value.contains("cà phê")
                || value.contains("ca phe");
    }

    private String formatDate(long timestamp) {
        try {
            return dateFormat.format(new Date(timestamp));
        } catch (Exception e) {
            return "Không rõ thời gian";
        }
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private TextView createText(String text, float size) {
        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setTextSize(size);
        textView.setTextColor(Color.rgb(45, 55, 45));
        return textView;
    }

    private int dp(float value) {
        return (int) (value * getResources()
                .getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (databaseHelper != null && recordContainer != null) {
            loadRecords();
        }
    }
}
