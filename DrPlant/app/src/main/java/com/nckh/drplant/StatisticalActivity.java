
package com.nckh.drplant;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class StatisticalActivity extends AppCompatActivity {

    private Spinner spinnerArea;

    private TextView tvTotalPlants;
    private TextView tvSickPlants;
    private TextView tvDiseaseRate;
    private TextView tvDiseaseTypes;

    private FrameLayout chartPlantDisease;
    private FrameLayout chartDiseaseStatistics;

    private TextView tvPlantChartEmpty;
    private TextView tvDiseaseChartEmpty;
    private TextView tvMostCommonDisease;

    private Button btnViewStatistics;

    private String selectedAreaId = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistical);

        initializeViews();
        initializeAreaSpinner();
        initializeListeners();
        showEmptyStatistics();
    }

    private void initializeViews() {
        spinnerArea = findViewById(R.id.spinnerArea);

        tvTotalPlants = findViewById(R.id.tvTotalPlants);
        tvSickPlants = findViewById(R.id.tvSickPlants);
        tvDiseaseRate = findViewById(R.id.tvDiseaseRate);
        tvDiseaseTypes = findViewById(R.id.tvDiseaseTypes);

        chartPlantDisease = findViewById(R.id.chartPlantDisease);
        chartDiseaseStatistics = findViewById(R.id.chartDiseaseStatistics);

        tvPlantChartEmpty = findViewById(R.id.tvPlantChartEmpty);
        tvDiseaseChartEmpty = findViewById(R.id.tvDiseaseChartEmpty);

        tvMostCommonDisease = findViewById(R.id.tvMostCommonDisease);
        btnViewStatistics = findViewById(R.id.btnViewStatistics);
    }

    private void initializeAreaSpinner() {
        // Chưa có nguồn dữ liệu khu vực nên không tạo khu vực giả.
        List<String> areaNames = new ArrayList<>();

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                areaNames
        );

        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spinnerArea.setAdapter(adapter);
        spinnerArea.setEnabled(false);
    }

    private void initializeListeners() {
        spinnerArea.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            View view,
                            int position,
                            long id
                    ) {
                        /*
                         * Khi kết nối cơ sở dữ liệu:
                         * 1. Lấy ID khu vực đã chọn.
                         * 2. Truy vấn dữ liệu cây và bệnh.
                         * 3. Gọi updateStatistics() với kết quả thật.
                         */
                    }

                    @Override
                    public void onNothingSelected(
                            AdapterView<?> parent
                    ) {
                        selectedAreaId = null;
                        showEmptyStatistics();
                    }
                }
        );

        btnViewStatistics.setOnClickListener(view -> {
            /*
             * TODO: Mở màn hình thống kê chi tiết
             * sau khi tạo màn hình đó.
             */
        });

        findViewById(R.id.navHome).setOnClickListener(v -> {
            Intent intent = new Intent(StatisticalActivity.this, HomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        findViewById(R.id.navArea).setOnClickListener(v ->
                Toast.makeText(this, "Tính năng Khu vực đang phát triển", Toast.LENGTH_SHORT).show()
        );

        findViewById(R.id.navPlants).setOnClickListener(v ->
                Toast.makeText(this, "Tính năng Cây trồng đang phát triển", Toast.LENGTH_SHORT).show()
        );

        findViewById(R.id.navDisease).setOnClickListener(v ->
                Toast.makeText(this, "Tính năng Bệnh cây đang phát triển", Toast.LENGTH_SHORT).show()
        );

        findViewById(R.id.navStats).setOnClickListener(v -> {
            // Đang ở màn hình Thống kê
        });
    }

    private void showEmptyStatistics() {
        String placeholder =
                getString(R.string.statistic_value_placeholder);

        tvTotalPlants.setText(placeholder);
        tvSickPlants.setText(placeholder);
        tvDiseaseRate.setText(placeholder);
        tvDiseaseTypes.setText(placeholder);

        tvMostCommonDisease.setText(
                getString(R.string.most_common_disease_empty)
        );

        tvPlantChartEmpty.setText(
                getString(R.string.chart_empty)
        );
        tvDiseaseChartEmpty.setText(
                getString(R.string.chart_empty)
        );

        tvPlantChartEmpty.setVisibility(View.VISIBLE);
        tvDiseaseChartEmpty.setVisibility(View.VISIBLE);
    }

    /**
     * Cập nhật các chỉ số từ dữ liệu thật.
     * Không truyền dữ liệu mẫu vào phương thức này.
     */
    private void updateStatistics(
            int totalPlants,
            int sickPlants,
            int diseaseTypeCount,
            String mostCommonDisease
    ) {
        if (totalPlants < 0
                || sickPlants < 0
                || diseaseTypeCount < 0
                || sickPlants > totalPlants) {
            showEmptyStatistics();
            return;
        }

        tvTotalPlants.setText(String.valueOf(totalPlants));
        tvSickPlants.setText(String.valueOf(sickPlants));
        tvDiseaseTypes.setText(String.valueOf(diseaseTypeCount));

        double rate = totalPlants == 0
                ? 0.0
                : sickPlants * 100.0 / totalPlants;

        tvDiseaseRate.setText(
                String.format(Locale.getDefault(), "%.1f%%", rate)
        );

        if (mostCommonDisease == null
                || mostCommonDisease.trim().isEmpty()) {
            tvMostCommonDisease.setText(
                    getString(R.string.most_common_disease_empty)
            );
        } else {
            tvMostCommonDisease.setText(mostCommonDisease);
        }
    }
}
