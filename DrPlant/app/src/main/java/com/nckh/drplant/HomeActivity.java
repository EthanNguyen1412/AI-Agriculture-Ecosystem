package com.nckh.drplant;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.nckh.drplant.Models.DiagnosisHistoryItem;
import com.nckh.drplant.Models.DiagnosisResponse;
import com.nckh.drplant.Utils.DiagnosisHelper;
import com.nckh.drplant.Utils.DiagnosisHistoryManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Trang chủ của DrPlant: màn hình đầu tiên sau khi đăng nhập.
 * Là nơi điều hướng tới Camera (chẩn đoán), Lịch sử, Thống kê và các chức năng sắp phát triển.
 */
public class HomeActivity extends AppCompatActivity {

    private static final int RECENT_LIMIT = 3;

    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

    private TextView tvTotal;
    private TextView tvSick;
    private TextView tvRecentEmpty;
    private LinearLayout recentContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Chưa đăng nhập thì quay về màn hình đăng nhập.
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            goToLogin();
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        tvTotal = findViewById(R.id.tvHomeTotal);
        tvSick = findViewById(R.id.tvHomeSick);
        tvRecentEmpty = findViewById(R.id.tvHomeRecentEmpty);
        recentContainer = findViewById(R.id.homeRecentContainer);

        TextView tvGreeting = findViewById(R.id.tvHomeGreeting);
        tvGreeting.setText(getString(R.string.home_greeting, getDisplayName(user)));

        findViewById(R.id.btnHomeLogout).setOnClickListener(v -> confirmLogout());
        findViewById(R.id.btnHomeStartDiagnosis).setOnClickListener(v ->
                startActivity(new Intent(this, CameraActivity.class)));

        findViewById(R.id.cardHomeHistory).setOnClickListener(v ->
                startActivity(new Intent(this, HistoryActivity.class)));
        findViewById(R.id.tvHomeSeeAll).setOnClickListener(v ->
                startActivity(new Intent(this, HistoryActivity.class)));
        findViewById(R.id.cardHomeStats).setOnClickListener(v ->
                startActivity(new Intent(this, StatisticalActivity.class)));

        findViewById(R.id.cardHomeArea).setOnClickListener(v -> showComingSoon());
        findViewById(R.id.cardHomePlants).setOnClickListener(v -> showComingSoon());
        findViewById(R.id.cardHomeDisease).setOnClickListener(v -> showComingSoon());
    }

    // Làm mới số liệu và danh sách gần đây mỗi khi quay lại trang chủ (sau khi chẩn đoán xong).
    @Override
    protected void onResume() {
        super.onResume();
        if (recentContainer != null) {
            refreshSummary();
        }
    }

    private void refreshSummary() {
        List<DiagnosisHistoryItem> items =
                new ArrayList<>(DiagnosisHistoryManager.getHistoryItems(this));
        Collections.sort(items, (a, b) -> Long.compare(b.viewedAt, a.viewedAt));

        int sick = 0;
        for (DiagnosisHistoryItem item : items) {
            if (!item.healthy) {
                sick++;
            }
        }
        tvTotal.setText(String.valueOf(items.size()));
        tvSick.setText(String.valueOf(sick));

        recentContainer.removeAllViews();
        if (items.isEmpty()) {
            tvRecentEmpty.setVisibility(View.VISIBLE);
            recentContainer.setVisibility(View.GONE);
            return;
        }
        tvRecentEmpty.setVisibility(View.GONE);
        recentContainer.setVisibility(View.VISIBLE);

        int count = Math.min(RECENT_LIMIT, items.size());
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                recentContainer.addView(buildDivider());
            }
            recentContainer.addView(buildRecentRow(items.get(i)));
        }
    }

    // Dựng một hàng "chẩn đoán gần đây" từ layout item_home_recent.xml.
    private View buildRecentRow(@NonNull DiagnosisHistoryItem item) {
        View row = getLayoutInflater().inflate(R.layout.item_home_recent, recentContainer, false);

        String name = !TextUtils.isEmpty(item.diseaseName) ? item.diseaseName : item.predictionName;
        if (TextUtils.isEmpty(name)) {
            name = getString(R.string.home_recent_unknown);
        }

        TextView tvName = row.findViewById(R.id.tvRecentName);
        TextView tvDate = row.findViewById(R.id.tvRecentDate);
        TextView tvStatus = row.findViewById(R.id.tvRecentStatus);

        tvName.setText(name);
        tvDate.setText(dateFormat.format(new Date(item.viewedAt)));
        tvStatus.setText(item.healthy ? R.string.home_status_healthy : R.string.home_status_sick);
        tvStatus.setBackgroundResource(item.healthy ? R.drawable.bg_chip_healthy : R.drawable.bg_chip_sick);
        tvStatus.setTextColor(ContextCompat.getColor(this,
                item.healthy ? R.color.dp_primary : R.color.dp_danger));

        row.setOnClickListener(v -> openHistoryItem(item));
        return row;
    }

    private View buildDivider() {
        View divider = new View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        divider.setBackgroundColor(ContextCompat.getColor(this, R.color.dp_stroke));
        return divider;
    }

    // Mở chi tiết một lần chẩn đoán cũ, cùng cách HistoryActivity đang làm.
    private void openHistoryItem(@NonNull DiagnosisHistoryItem item) {
        DiagnosisResponse response = item.diagnosisResponse;
        if (response == null) {
            response = DiagnosisHistoryManager.buildDiagnosisResponseFromHistory(item);
            item.diagnosisResponse = response;
        }
        if (response == null) {
            Toast.makeText(this, R.string.history_open_detail_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        DiagnosisHelper.launchDiagnosisActivityFromHistory(this, response, item.imagePath);
    }

    private void showComingSoon() {
        Toast.makeText(this, R.string.home_coming_soon, Toast.LENGTH_SHORT).show();
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.auth_logout_title)
                .setMessage(R.string.auth_logout_message)
                .setPositiveButton(R.string.auth_logout_confirm, (d, w) -> {
                    FirebaseAuth.getInstance().signOut();
                    goToLogin();
                })
                .setNegativeButton(R.string.auth_logout_cancel, null)
                .show();
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private String getDisplayName(@NonNull FirebaseUser user) {
        String name = user.getDisplayName();
        if (!TextUtils.isEmpty(name)) {
            return name;
        }
        String email = user.getEmail();
        if (!TextUtils.isEmpty(email)) {
            int at = email.indexOf('@');
            return at > 0 ? email.substring(0, at) : email;
        }
        return getString(R.string.home_default_name);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
