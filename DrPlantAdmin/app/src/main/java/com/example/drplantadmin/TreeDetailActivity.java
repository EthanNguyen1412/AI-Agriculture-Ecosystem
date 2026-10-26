package com.example.drplantadmin;


import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.example.drplantadmin.adapter.HistoryAdapter;
import com.example.drplantadmin.data.AdminRepository;
import com.example.drplantadmin.Models.Diagnosis;
import com.example.drplantadmin.Models.Disease;
import com.example.drplantadmin.Models.Plant;
import com.example.drplantadmin.Models.Region;
import com.example.drplantadmin.Models.Tree;
import java.util.List;

/** Chi tiết một cây: thông tin, tình trạng hiện tại, gợi ý chăm sóc và lịch sử chẩn đoán. */
public class TreeDetailActivity extends AppCompatActivity implements HistoryAdapter.Listener {

    public static final String EXTRA_TREE_ID = "tree_id";

    private AdminRepository repo;
    private String treeId;
    private HistoryAdapter adapter;
    private TextView tvTitle, tvSubtitle, tvStatus, tvDisease, tvInfo, tvCare, tvNoHistory;
    private View cardCare;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tree_detail);
        repo = AdminRepository.get(this);

        treeId = getIntent().getStringExtra(EXTRA_TREE_ID);
        if (treeId == null || repo.getTree(treeId) == null) {
            finish();
            return;
        }

        tvTitle = findViewById(R.id.tvTitle);
        tvSubtitle = findViewById(R.id.tvSubtitle);
        tvStatus = findViewById(R.id.tvStatus);
        tvDisease = findViewById(R.id.tvDisease);
        tvInfo = findViewById(R.id.tvInfo);
        tvCare = findViewById(R.id.tvCare);
        tvNoHistory = findViewById(R.id.tvNoHistory);
        cardCare = findViewById(R.id.cardCare);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        RecyclerView rv = findViewById(R.id.rvHistory);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HistoryAdapter(this, this);
        rv.setAdapter(adapter);

        // Sau này nối với CameraActivity/DiagnosisActivity của app chính
        findViewById(R.id.btnDiagnose).setOnClickListener(v ->
                Toast.makeText(this, "Sẽ nối với camera của app chính", Toast.LENGTH_SHORT).show());

        findViewById(R.id.btnTestDiag).setOnClickListener(v -> {
            Tree t = repo.getTree(treeId);
            if (t != null) FormDialogs.testDiagnosis(this, t, this::refresh);
        });
        findViewById(R.id.btnEdit).setOnClickListener(v -> {
            Tree t = repo.getTree(treeId);
            if (t != null) FormDialogs.treeForm(this, t.regionId, t, this::refresh);
        });
        findViewById(R.id.btnMove).setOnClickListener(v -> {
            Tree t = repo.getTree(treeId);
            if (t != null) FormDialogs.moveTree(this, t, this::refresh);
        });
        findViewById(R.id.btnDelete).setOnClickListener(v -> {
            Tree t = repo.getTree(treeId);
            if (t != null) FormDialogs.confirmDeleteTree(this, t, this::finish);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (treeId != null) refresh();
    }

    private void refresh() {
        Tree tree = repo.getTree(treeId);
        if (tree == null) {
            finish();
            return;
        }
        Region region = repo.getRegion(tree.regionId);
        tvTitle.setText(tree.code);
        tvSubtitle.setText(region == null ? "" : region.name);

        int status = repo.statusOf(treeId);
        Diagnosis last = repo.latest(treeId);
        UiUtil.bindStatus(tvStatus, status);

        cardCare.setVisibility(View.GONE);
        if (status == AdminRepository.STATUS_SICK) {
            Disease d = repo.getDisease(last.diseaseId);
            tvDisease.setText(d != null ? d.name : last.diseaseName);
            if (d != null) {
                tvCare.setText("Điều trị:\n" + nz(d.treatment) + "\n\nPhòng ngừa:\n" + nz(d.prevention));
                cardCare.setVisibility(View.VISIBLE);
            }
        } else if (status == AdminRepository.STATUS_HEALTHY) {
            tvDisease.setText("Khỏe mạnh");
        } else {
            tvDisease.setText("Chưa có kết quả");
        }

        tvInfo.setText("Số gốc: " + tree.code
                + "\nVùng: " + (region == null ? "-" : region.name)
                + "\nLoại cây: " + Plant.label(tree.plant)
                + "\nNgày thêm: " + UiUtil.formatDate(tree.createdAt)
                + "\nLần chẩn đoán gần nhất: " + (last == null ? "-" : UiUtil.formatTime(last.time)));

        List<Diagnosis> history = repo.getDiagnoses(treeId);
        adapter.submit(history);
        tvNoHistory.setVisibility(history.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private static String nz(String s) {
        return s == null || s.isEmpty() ? "(chưa có thông tin)" : s;
    }

    /** Bấm vào một lần chẩn đoán để xem triệu chứng và cách chăm sóc. */
    @Override
    public void onOpen(Diagnosis diagnosis) {
        Disease d = repo.getDisease(diagnosis.diseaseId);
        String title;
        String message;
        if (diagnosis.diseaseId == null) {
            title = "Khỏe mạnh";
            message = "Lá khỏe, chưa phát hiện bệnh. Tiếp tục tưới nước, bón phân điều độ và theo dõi định kỳ.";
        } else if (d == null) {
            title = diagnosis.diseaseName;
            message = "Bệnh này đã bị xóa khỏi danh sách nên không còn thông tin chăm sóc.";
        } else {
            title = d.name;
            message = "Triệu chứng:\n" + nz(d.symptoms)
                    + "\n\nNguyên nhân:\n" + nz(d.causes)
                    + "\n\nPhòng ngừa:\n" + nz(d.prevention)
                    + "\n\nĐiều trị:\n" + nz(d.treatment);
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(title + " · " + UiUtil.formatDate(diagnosis.time))
                .setMessage(message)
                .setPositiveButton("Đóng", null)
                .show();
    }
}
