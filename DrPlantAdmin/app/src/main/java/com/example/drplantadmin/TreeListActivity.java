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
import com.example.drplantadmin.adapter.TreeAdapter;
import com.example.drplantadmin.data.AdminRepository;
import com.example.drplantadmin.Models.Plant;
import com.example.drplantadmin.Models.Region;
import com.example.drplantadmin.Models.Tree;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Quản lý cây (đánh số gốc) của MỘT vùng. Nút back quay lại danh sách vùng để chọn vùng khác. */
public class TreeListActivity extends AppCompatActivity implements TreeAdapter.Listener {

    public static final String EXTRA_REGION_ID = "region_id";

    private AdminRepository repo;
    private String regionId;
    private TreeAdapter adapter;
    private TextView tvEmpty, tvTotal, tvSick, tvRate, tvTitle, tvSubtitle;
    private TextInputEditText searchInput;
    private ChipGroup chipStatus, chipPlant;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tree_list);
        repo = AdminRepository.get(this);

        regionId = getIntent().getStringExtra(EXTRA_REGION_ID);
        if (regionId == null || repo.getRegion(regionId) == null) {
            finish();
            return;
        }

        tvTitle = findViewById(R.id.tvTitle);
        tvSubtitle = findViewById(R.id.tvSubtitle);
        tvTotal = findViewById(R.id.tvTotal);
        tvSick = findViewById(R.id.tvSick);
        tvRate = findViewById(R.id.tvRate);
        tvEmpty = findViewById(R.id.tvEmpty);
        searchInput = findViewById(R.id.searchInput);
        chipStatus = findViewById(R.id.chipStatus);
        chipPlant = findViewById(R.id.chipPlant);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        RecyclerView rv = findViewById(R.id.rv);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TreeAdapter(this, this);
        rv.setAdapter(adapter);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable e) { refresh(); }
        });
        chipStatus.setOnCheckedStateChangeListener((g, ids) -> refresh());
        chipPlant.setOnCheckedStateChangeListener((g, ids) -> refresh());

        findViewById(R.id.btnAddTree).setOnClickListener(v ->
                FormDialogs.treeForm(this, regionId, null, this::refresh));
        findViewById(R.id.btnBulk).setOnClickListener(v ->
                FormDialogs.bulkForm(this, regionId, this::refresh));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (regionId != null) refresh(); // cập nhật khi quay về từ chi tiết cây
    }

    private int statusFilter() {
        int id = chipStatus.getCheckedChipId();
        if (id == R.id.chipHealthy) return AdminRepository.STATUS_HEALTHY;
        if (id == R.id.chipSick) return AdminRepository.STATUS_SICK;
        if (id == R.id.chipNone) return AdminRepository.STATUS_NONE;
        return -1; // tất cả
    }

    private String plantFilter() {
        int id = chipPlant.getCheckedChipId();
        if (id == R.id.chipCoffee) return Plant.COFFEE;
        if (id == R.id.chipDurian) return Plant.DURIAN;
        return null;
    }

    private void refresh() {
        Region region = repo.getRegion(regionId);
        if (region == null) {
            finish();
            return;
        }
        tvTitle.setText(region.name);
        tvSubtitle.setText(region.location + " · " + Plant.label(region.plant));

        int[] s = repo.stats(regionId);
        tvTotal.setText(String.valueOf(s[0]));
        tvSick.setText(String.valueOf(s[1]));
        tvRate.setText(UiUtil.percent(s[1], s[0]));

        String q = UiUtil.text(searchInput).toLowerCase(Locale.ROOT);
        int statusFilter = statusFilter();
        String plantFilter = plantFilter();

        List<Tree> out = new ArrayList<>();
        for (Tree t : repo.getTrees(regionId)) {
            if (!q.isEmpty() && !t.code.toLowerCase(Locale.ROOT).contains(q)) continue;
            if (plantFilter != null && !plantFilter.equals(t.plant)) continue;
            if (statusFilter >= 0 && repo.statusOf(t.id) != statusFilter) continue;
            out.add(t);
        }
        adapter.submit(out);
        tvEmpty.setText(s[0] == 0 ? "Vùng này chưa có cây.\nBấm “Thêm cây” hoặc “Thêm hàng loạt”."
                : "Không có cây nào phù hợp.");
        tvEmpty.setVisibility(out.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onOpen(Tree tree) {
        Intent i = new Intent(this, TreeDetailActivity.class);
        i.putExtra(TreeDetailActivity.EXTRA_TREE_ID, tree.id);
        startActivity(i);
    }

    @Override
    public void onEdit(Tree tree) {
        FormDialogs.treeForm(this, regionId, tree, this::refresh);
    }

    @Override
    public void onMove(Tree tree) {
        FormDialogs.moveTree(this, tree, this::refresh);
    }

    @Override
    public void onDelete(Tree tree) {
        FormDialogs.confirmDeleteTree(this, tree, this::refresh);
    }
}
