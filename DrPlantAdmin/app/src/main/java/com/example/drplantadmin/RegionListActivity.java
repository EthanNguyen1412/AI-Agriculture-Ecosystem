package com.example.drplantadmin;


import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.drplantadmin.adapter.RegionAdapter;
import com.example.drplantadmin.data.AdminRepository;
import com.example.drplantadmin.Models.Region;
import java.util.List;

/** Quản lý vùng. Bấm vào một vùng để mở danh sách cây (số gốc) của vùng đó. */
public class RegionListActivity extends AppCompatActivity implements RegionAdapter.Listener {

    private AdminRepository repo;
    private RegionAdapter adapter;
    private TextView tvEmpty;
    private TextView tvSubtitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_region_list);
        repo = AdminRepository.get(this);

        ((TextView) findViewById(R.id.tvTitle)).setText("Quản lý vùng");
        tvSubtitle = findViewById(R.id.tvSubtitle);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        tvEmpty = findViewById(R.id.tvEmpty);
        RecyclerView rv = findViewById(R.id.rv);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RegionAdapter(this, this);
        rv.setAdapter(adapter);

        findViewById(R.id.btnAdd).setOnClickListener(v ->
                FormDialogs.regionForm(this, null, this::refresh));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh(); // cập nhật lại số cây khi quay về từ màn hình cây
    }

    private void refresh() {
        List<Region> regions = repo.getRegions();
        adapter.submit(regions);
        tvEmpty.setVisibility(regions.isEmpty() ? View.VISIBLE : View.GONE);
        tvSubtitle.setText(regions.size() + " vùng · chọn vùng để quản lý cây");
    }

    @Override
    public void onOpen(Region region) {
        Intent i = new Intent(this, TreeListActivity.class);
        i.putExtra(TreeListActivity.EXTRA_REGION_ID, region.id);
        startActivity(i);
    }

    @Override
    public void onEdit(Region region) {
        FormDialogs.regionForm(this, region, this::refresh);
    }

    @Override
    public void onDelete(Region region) {
        FormDialogs.confirmDeleteRegion(this, region, this::refresh);
    }
}
