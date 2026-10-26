package com.example.drplantadmin;


import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.example.drplantadmin.adapter.DiseaseAdapter;
import com.example.drplantadmin.data.AdminRepository;
import com.example.drplantadmin.Models.Disease;
import com.example.drplantadmin.Models.Plant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Quản lý bệnh: danh sách, tìm kiếm, lọc theo loại cây, thêm bệnh. */
public class DiseaseListActivity extends AppCompatActivity implements DiseaseAdapter.Listener {

    private AdminRepository repo;
    private DiseaseAdapter adapter;
    private TextView tvEmpty;
    private TextInputEditText searchInput;
    private ChipGroup chipPlant;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_disease_list);
        repo = AdminRepository.get(this);

        ((TextView) findViewById(R.id.tvTitle)).setText("Quản lý bệnh");
        ((TextView) findViewById(R.id.tvSubtitle)).setText("Thông tin bệnh và cách chăm sóc");
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        tvEmpty = findViewById(R.id.tvEmpty);
        searchInput = findViewById(R.id.searchInput);
        chipPlant = findViewById(R.id.chipPlant);

        RecyclerView rv = findViewById(R.id.rv);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DiseaseAdapter(this);
        rv.setAdapter(adapter);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable e) { refresh(); }
        });
        chipPlant.setOnCheckedStateChangeListener((g, ids) -> refresh());

        findViewById(R.id.btnAdd).setOnClickListener(v ->
                FormDialogs.diseaseForm(this, null, this::refresh));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh(); // cập nhật sau khi sửa/xóa ở màn chi tiết
    }

    private void refresh() {
        String q = UiUtil.text(searchInput).toLowerCase(Locale.ROOT);
        int chip = chipPlant.getCheckedChipId();
        String plant = chip == R.id.chipCoffee ? Plant.COFFEE
                : chip == R.id.chipDurian ? Plant.DURIAN : null;

        List<Disease> out = new ArrayList<>();
        for (Disease d : repo.getDiseases()) {
            if (!q.isEmpty() && !d.name.toLowerCase(Locale.ROOT).contains(q)) continue;
            if (plant != null && !plant.equals(d.plant)) continue;
            out.add(d);
        }
        adapter.submit(out);
        tvEmpty.setVisibility(out.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onOpen(Disease disease) {
        Intent i = new Intent(this, DiseaseDetailActivity.class);
        i.putExtra(DiseaseDetailActivity.EXTRA_DISEASE_ID, disease.id);
        startActivity(i);
    }
}
