package com.example.drplantadmin;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class RecordDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "drplant_admin.db";
    private static final int DATABASE_VERSION = 1;

    public RecordDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE DiagnosisRecords (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "plantType TEXT NOT NULL, " +
                        "status TEXT NOT NULL, " +
                        "diseaseName TEXT NOT NULL, " +
                        "diagnosedAt INTEGER NOT NULL, " +
                        "imagePath TEXT, " +
                        "careAdvice TEXT)"
        );

        long now = System.currentTimeMillis();

        insertSample(db, "Sầu riêng", "HEALTHY",
                "Khỏe mạnh", now - 3600000L,
                "Theo dõi độ ẩm và chăm sóc cây định kỳ.");

        insertSample(db, "Cà phê", "DISEASE",
                "Đốm lá (dữ liệu minh họa)", now - 7200000L,
                "Kiểm tra lá và xác định nguyên nhân trước khi xử lý.");

        insertSample(db, "Sầu riêng", "DISEASE",
                "Bất thường trên lá (dữ liệu minh họa)",
                now - 86400000L,
                "Kiểm tra thêm các lá xung quanh và theo dõi diễn biến.");
    }

    private void insertSample(
            SQLiteDatabase db,
            String plantType,
            String status,
            String diseaseName,
            long diagnosedAt,
            String careAdvice) {

        ContentValues values = new ContentValues();
        values.put("plantType", plantType);
        values.put("status", status);
        values.put("diseaseName", diseaseName);
        values.put("diagnosedAt", diagnosedAt);
        values.put("imagePath", "");
        values.put("careAdvice", careAdvice);

        db.insert("DiagnosisRecords", null, values);
    }

    @Override
    public void onUpgrade(
            SQLiteDatabase db, int oldVersion, int newVersion) {
        // Chưa có thay đổi cấu trúc ở phiên bản này.
    }

    public long addRecord(
            String plantType,
            String status,
            String diseaseName,
            String imagePath,
            String careAdvice) {

        SQLiteDatabase db = getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("plantType", plantType);
        values.put("status", status);
        values.put("diseaseName", diseaseName);
        values.put("diagnosedAt", System.currentTimeMillis());
        values.put("imagePath", imagePath);
        values.put("careAdvice", careAdvice);

        return db.insert("DiagnosisRecords", null, values);
    }

    public List<DiagnosisRecord> getAllRecords() {
        List<DiagnosisRecord> records = new ArrayList<>();

        SQLiteDatabase db = getReadableDatabase();

        try (Cursor cursor = db.query(
                "DiagnosisRecords",
                null,
                null,
                null,
                null,
                null,
                "diagnosedAt DESC")) {

            while (cursor.moveToNext()) {
                records.add(new DiagnosisRecord(
                        cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        cursor.getString(cursor.getColumnIndexOrThrow("plantType")),
                        cursor.getString(cursor.getColumnIndexOrThrow("status")),
                        cursor.getString(cursor.getColumnIndexOrThrow("diseaseName")),
                        cursor.getLong(cursor.getColumnIndexOrThrow("diagnosedAt")),
                        cursor.getString(cursor.getColumnIndexOrThrow("imagePath")),
                        cursor.getString(cursor.getColumnIndexOrThrow("careAdvice"))
                ));
            }
        }


        return records;
    }

    public int updateRecord(
            long id,
            String plantType,
            String status,
            String diseaseName,
            String careAdvice
    ) {
        SQLiteDatabase db = getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("plantType", plantType);
        values.put("status", status);
        values.put("diseaseName", diseaseName);
        values.put("careAdvice", careAdvice);

        return db.update(
                "DiagnosisRecords",
                values,
                "id = ?",
                new String[]{String.valueOf(id)}
        );
    }

    public int deleteRecord(long id) {
        SQLiteDatabase db = getWritableDatabase();

        return db.delete(
                "DiagnosisRecords",
                "id = ?",
                new String[]{String.valueOf(id)}
        );
    }
}

