package com.example.drplantadmin;

import android.os.Bundle;
import android.content.Intent;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        LinearLayout btnManageRecords =
                findViewById(R.id.btnManageRecords);

        LinearLayout btnManageRegions =
                findViewById(R.id.btnManageRegions);

        LinearLayout btnManageAccounts =
                findViewById(R.id.btnManageAccounts);

        // Chuyển sang màn hình quản lý bản ghi
        btnManageRecords.setOnClickListener(v -> {
            Intent intent = new Intent(
                    MainActivity.this,
                    RecordListActivity.class
            );
            startActivity(intent);
        });

        // Nút quản lý vùng: mở danh sách vùng
        btnManageRegions.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, RegionListActivity.class)));

        // Thẻ thứ ba (trước là quản lý tài khoản): giờ là quản lý bệnh
        btnManageAccounts.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, DiseaseListActivity.class)));
    }
}
