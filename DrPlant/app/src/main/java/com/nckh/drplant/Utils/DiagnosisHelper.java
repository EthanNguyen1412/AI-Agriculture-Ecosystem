package com.nckh.drplant.Utils;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.nckh.drplant.DiagnosisActivity;
import com.nckh.drplant.Models.DiagnosisResponse;

public class DiagnosisHelper {

    private static final String TAG = "DiagnosisHelper";

    private DiagnosisHelper() {
        // Utility class
    }

    // Mở màn hình chẩn đoán bằng dữ liệu object đã được tạo sẵn từ model local.
    public static void launchDiagnosisActivity(Context context, DiagnosisResponse diagnosisData) {
        launchDiagnosisActivity(context, diagnosisData, null, true, false);
    }

    public static void launchDiagnosisActivity(Context context, DiagnosisResponse diagnosisData, String imagePath) {
        launchDiagnosisActivity(context, diagnosisData, imagePath, true, false);
    }

    public static void launchDiagnosisActivity(Context context, DiagnosisResponse diagnosisData, String imagePath, boolean saveToHistory) {
        launchDiagnosisActivity(context, diagnosisData, imagePath, saveToHistory, false);
    }

    // Mở lại màn hình chi tiết chẩn đoán từ danh sách lịch sử mà không lưu trùng lịch sử lần nữa.
    public static void launchDiagnosisActivityFromHistory(Context context, DiagnosisResponse diagnosisData, String imagePath) {
        launchDiagnosisActivity(context, diagnosisData, imagePath, false, true);
    }

    private static void launchDiagnosisActivity(Context context,
                                                DiagnosisResponse diagnosisData,
                                                String imagePath,
                                                boolean saveToHistory,
                                                boolean openedFromHistory) {
        try {
            Intent intent = new Intent(context, DiagnosisActivity.class);
            intent.putExtra(DiagnosisActivity.EXTRA_DIAGNOSIS_DATA, diagnosisData);
            intent.putExtra(DiagnosisActivity.EXTRA_CAPTURED_IMAGE_PATH, imagePath);
            intent.putExtra(DiagnosisActivity.EXTRA_SAVE_TO_HISTORY, saveToHistory);
            intent.putExtra(DiagnosisActivity.EXTRA_OPENED_FROM_HISTORY, openedFromHistory);
            addNewTaskFlagIfNeeded(context, intent);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Unable to launch diagnosis activity from object", e);
        }
    }

    private static void addNewTaskFlagIfNeeded(Context context, Intent intent) {
        if (!(context instanceof android.app.Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
    }
}

