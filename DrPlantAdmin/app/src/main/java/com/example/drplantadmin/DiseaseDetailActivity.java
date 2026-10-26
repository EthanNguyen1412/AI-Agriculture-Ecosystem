package com.example.drplantadmin;


import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.drplantadmin.data.AdminRepository;
import com.example.drplantadmin.Models.Disease;
import com.example.drplantadmin.Models.Plant;

/** Chi tiết bệnh: triệu chứng, nguyên nhân, phòng ngừa, điều trị. Có cập nhật và xóa. */
public class DiseaseDetailActivity extends AppCompatActivity {

    public static final String EXTRA_DISEASE_ID = "disease_id";

    private AdminRepository repo;
    private String diseaseId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_disease_detail);
        repo = AdminRepository.get(this);

        diseaseId = getIntent().getStringExtra(EXTRA_DISEASE_ID);
        if (diseaseId == null || repo.getDisease(diseaseId) == null) {
            finish();
            return;
        }
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        findViewById(R.id.btnEdit).setOnClickListener(v -> {
            Disease d = repo.getDisease(diseaseId);
            if (d != null) FormDialogs.diseaseForm(this, d, this::bind);
        });
        findViewById(R.id.btnDelete).setOnClickListener(v -> {
            Disease d = repo.getDisease(diseaseId);
            if (d != null) FormDialogs.confirmDeleteDisease(this, d, this::finish);
        });
        bind();
    }

    private void bind() {
        Disease d = repo.getDisease(diseaseId);
        if (d == null) {
            finish();
            return;
        }
        ((TextView) findViewById(R.id.tvTitle)).setText(d.name);
        ((TextView) findViewById(R.id.tvSubtitle)).setText(Plant.label(d.plant));
        set(R.id.tvSymptoms, d.symptoms);
        set(R.id.tvCauses, d.causes);
        set(R.id.tvPrevention, d.prevention);
        set(R.id.tvTreatment, d.treatment);

        // Bệnh gốc của model: không cho xóa
        View delete = findViewById(R.id.btnDelete);
        delete.setEnabled(!d.builtIn);
        delete.setAlpha(d.builtIn ? 0.4f : 1f);
        findViewById(R.id.tvBuiltInNote).setVisibility(d.builtIn ? View.VISIBLE : View.GONE);
    }

    private void set(int id, String value) {
        ((TextView) findViewById(id)).setText(
                value == null || value.isEmpty() ? "(chưa có thông tin)" : value);
    }
}
