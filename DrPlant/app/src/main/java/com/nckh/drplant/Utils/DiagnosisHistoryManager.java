package com.nckh.drplant.Utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.nckh.drplant.Models.DiagnosisHistoryItem;
import com.nckh.drplant.Models.DiagnosisResponse;

import java.lang.reflect.Type;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class DiagnosisHistoryManager {

    public static final String FILTER_ALL = "all";
    public static final String FILTER_DURIAN = "durian";
    public static final String FILTER_COFFEE = "coffee";

    private static final String PREFS_NAME = "diagnosis_history_prefs";
    private static final String KEY_HISTORY = "history_items";
    private static final int MAX_HISTORY_ITEMS = 100;
    private static final Gson GSON = new Gson();
    private static final Type HISTORY_LIST_TYPE = new TypeToken<List<DiagnosisHistoryItem>>() { }.getType();

    private DiagnosisHistoryManager() {
        // Utility class
    }

    public static void saveHistoryItem(@NonNull Context context,
                                       @NonNull DiagnosisResponse diagnosisResponse,
                                       @Nullable String imagePath) {
        List<DiagnosisHistoryItem> historyItems = getHistoryItems(context);

        DiagnosisHistoryItem historyItem = new DiagnosisHistoryItem();
        historyItem.id = String.valueOf(System.currentTimeMillis());
        historyItem.viewedAt = System.currentTimeMillis();
        historyItem.imagePath = imagePath;
        historyItem.diagnosisResponse = diagnosisResponse;
        populateHistoryItemDetails(historyItem, diagnosisResponse);

        historyItems.add(0, historyItem);
        if (historyItems.size() > MAX_HISTORY_ITEMS) {
            historyItems = new ArrayList<>(historyItems.subList(0, MAX_HISTORY_ITEMS));
        }

        saveHistoryItems(context, historyItems);
    }

    @NonNull
    public static List<DiagnosisHistoryItem> getHistoryItems(@NonNull Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String historyJson = sharedPreferences.getString(KEY_HISTORY, "");
        if (TextUtils.isEmpty(historyJson)) {
            return new ArrayList<>();
        }

        List<DiagnosisHistoryItem> historyItems = GSON.fromJson(historyJson, HISTORY_LIST_TYPE);
        if (historyItems == null) {
            return new ArrayList<>();
        }

        List<DiagnosisHistoryItem> hydratedHistoryItems = new ArrayList<>(historyItems);
        boolean hasChanges = hydrateHistoryItems(hydratedHistoryItems);
        if (hasChanges) {
            saveHistoryItems(context, hydratedHistoryItems);
        }
        return hydratedHistoryItems;
    }

    @NonNull
    public static List<DiagnosisHistoryItem> filterHistory(@NonNull List<DiagnosisHistoryItem> sourceItems,
                                                           @NonNull String filter) {
        return filterHistory(sourceItems, filter, null);
    }

    @NonNull
    public static List<DiagnosisHistoryItem> filterHistory(@NonNull List<DiagnosisHistoryItem> sourceItems,
                                                           @NonNull String filter,
                                                           @Nullable Long selectedDateMillis) {
        List<DiagnosisHistoryItem> filteredItems = new ArrayList<>();
        long startOfDay = selectedDateMillis == null ? -1L : getStartOfDay(selectedDateMillis);
        long endOfDay = selectedDateMillis == null ? -1L : getEndOfDay(startOfDay);

        for (DiagnosisHistoryItem item : sourceItems) {
            if (item == null) {
                continue;
            }

            if (!matchesPlantFilter(item, filter)) {
                continue;
            }

            if (selectedDateMillis != null && !matchesDateFilter(item, startOfDay, endOfDay)) {
                continue;
            }

            filteredItems.add(item);
        }
        return filteredItems;
    }

    // Kiểm tra một mục lịch sử có phù hợp với bộ lọc loại cây hiện tại hay không.
    private static boolean matchesPlantFilter(@NonNull DiagnosisHistoryItem item, @NonNull String filter) {
        if (FILTER_ALL.equals(filter)) {
            return true;
        }

        String normalizedType = item.plantTypeNormalized;
        if (TextUtils.isEmpty(normalizedType)) {
            normalizedType = normalizePlantType(item.plantTypeRaw);
        }
        return filter.equals(normalizedType);
    }

    // Kiểm tra một mục lịch sử có thuộc đúng ngày mà người dùng đã chọn hay không.
    private static boolean matchesDateFilter(@NonNull DiagnosisHistoryItem item, long startOfDay, long endOfDay) {
        return item.viewedAt >= startOfDay && item.viewedAt < endOfDay;
    }

    // Chuẩn hóa mốc thời gian về đầu ngày để so sánh lịch sử theo ngày dương lịch.
    private static long getStartOfDay(long timestampMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestampMillis);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    // Tính mốc đầu ngày kế tiếp để tạo khoảng so sánh [đầu ngày, đầu ngày kế tiếp).
    private static long getEndOfDay(long startOfDay) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(startOfDay);
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        return calendar.getTimeInMillis();
    }

    // Xóa một mục chẩn đoán khỏi lịch sử đã lưu dựa trên id và trả về kết quả thao tác.
    public static boolean removeHistoryItem(@NonNull Context context, @Nullable String historyItemId) {
        if (TextUtils.isEmpty(historyItemId)) {
            return false;
        }

        List<DiagnosisHistoryItem> historyItems = getHistoryItems(context);
        boolean removed = false;
        for (int index = historyItems.size() - 1; index >= 0; index--) {
            DiagnosisHistoryItem item = historyItems.get(index);
            if (item != null && historyItemId.equals(item.id)) {
                historyItems.remove(index);
                removed = true;
                break;
            }
        }

        if (removed) {
            saveHistoryItems(context, historyItems);
        }
        return removed;
    }

    // Xóa nhiều mục chẩn đoán khỏi lịch sử đã lưu dựa trên danh sách id và trả về số lượng đã xóa.
    public static int removeHistoryItems(@NonNull Context context, @NonNull List<String> historyItemIds) {
        if (historyItemIds.isEmpty()) {
            return 0;
        }

        Set<String> idSet = new HashSet<>();
        for (String historyItemId : historyItemIds) {
            if (!TextUtils.isEmpty(historyItemId)) {
                idSet.add(historyItemId);
            }
        }
        if (idSet.isEmpty()) {
            return 0;
        }

        List<DiagnosisHistoryItem> historyItems = getHistoryItems(context);
        int removedCount = 0;
        for (int index = historyItems.size() - 1; index >= 0; index--) {
            DiagnosisHistoryItem item = historyItems.get(index);
            if (item != null && idSet.contains(item.id)) {
                historyItems.remove(index);
                removedCount++;
            }
        }

        if (removedCount > 0) {
            saveHistoryItems(context, historyItems);
        }
        return removedCount;
    }

    @NonNull
    public static String normalizePlantType(@Nullable String rawPlantType) {
        if (TextUtils.isEmpty(rawPlantType)) {
            return FILTER_ALL;
        }

        String normalized = Normalizer.normalize(rawPlantType, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z]", "");

        if (normalized.contains("saurieng") || normalized.contains("durian")) {
            return FILTER_DURIAN;
        }
        if (normalized.contains("coffee") || normalized.contains("cafe") || normalized.contains("caphe")) {
            return FILTER_COFFEE;
        }
        return FILTER_ALL;
    }

    // Đồng bộ dữ liệu chi tiết cho các bản ghi lịch sử cũ để luôn có thể mở lại màn hình chi tiết.
    private static boolean hydrateHistoryItems(@NonNull List<DiagnosisHistoryItem> historyItems) {
        boolean hasChanges = false;
        for (DiagnosisHistoryItem historyItem : historyItems) {
            if (historyItem == null) {
                continue;
            }

            if (historyItem.diagnosisResponse != null) {
                hasChanges |= populateHistoryItemDetails(historyItem, historyItem.diagnosisResponse);
                continue;
            }

            DiagnosisResponse rebuiltResponse = buildDiagnosisResponseFromHistory(historyItem);
            if (rebuiltResponse != null) {
                historyItem.diagnosisResponse = rebuiltResponse;
                hasChanges = true;
            }

            if (TextUtils.isEmpty(historyItem.plantTypeNormalized)) {
                historyItem.plantTypeNormalized = normalizePlantType(historyItem.plantTypeRaw);
                hasChanges = true;
            }
        }
        return hasChanges;
    }

    // Trích dữ liệu chi tiết từ DiagnosisResponse để lưu kèm, giúp lịch sử mở nhanh và bền hơn.
    private static boolean populateHistoryItemDetails(@NonNull DiagnosisHistoryItem historyItem,
                                                      @NonNull DiagnosisResponse diagnosisResponse) {
        boolean hasChanges = false;

        if (!areEqual(historyItem.message, diagnosisResponse.message)) {
            historyItem.message = diagnosisResponse.message;
            hasChanges = true;
        }

        if (diagnosisResponse.prediction != null) {
            DiagnosisResponse.Prediction prediction = diagnosisResponse.prediction;
            hasChanges |= setIfDifferent(historyItem.predictionClassId, prediction.classId, value -> historyItem.predictionClassId = value);
            hasChanges |= setIfDifferent(historyItem.predictionName, prediction.className, value -> historyItem.predictionName = value);
            hasChanges |= setIfDifferent(historyItem.plantTypeRaw, prediction.plantType, value -> historyItem.plantTypeRaw = value);

            String normalizedPlantType = normalizePlantType(prediction.plantType);
            if (!areEqual(historyItem.plantTypeNormalized, normalizedPlantType)) {
                historyItem.plantTypeNormalized = normalizedPlantType;
                hasChanges = true;
            }
            if (Double.compare(historyItem.confidence, prediction.confidence) != 0) {
                historyItem.confidence = prediction.confidence;
                hasChanges = true;
            }
            if (historyItem.healthy != prediction.isHealthy) {
                historyItem.healthy = prediction.isHealthy;
                hasChanges = true;
            }
        } else if (TextUtils.isEmpty(historyItem.plantTypeNormalized)) {
            historyItem.plantTypeNormalized = FILTER_ALL;
            hasChanges = true;
        }

        if (diagnosisResponse.disease != null) {
            DiagnosisResponse.Disease disease = diagnosisResponse.disease;
            hasChanges |= setIfDifferent(historyItem.diseaseName, disease.name, value -> historyItem.diseaseName = value);
            hasChanges |= setIfDifferent(historyItem.symptoms, disease.symptoms, value -> historyItem.symptoms = value);
            hasChanges |= setIfDifferent(historyItem.causes, disease.causes, value -> historyItem.causes = value);

            if (disease.severity != null) {
                DiagnosisResponse.Severity severity = disease.severity;
                hasChanges |= setIfDifferent(historyItem.severityLevel, severity.level, value -> historyItem.severityLevel = value);
                hasChanges |= setIfDifferent(historyItem.severityLabel, severity.label, value -> historyItem.severityLabel = value);
                hasChanges |= setIfDifferent(historyItem.severityColor, severity.color, value -> historyItem.severityColor = value);
                hasChanges |= setIfDifferent(historyItem.severityIcon, severity.icon, value -> historyItem.severityIcon = value);
            }
        }

        if (diagnosisResponse.solutions != null) {
            if (diagnosisResponse.solutions.chemical != null) {
                DiagnosisResponse.Solution chemical = diagnosisResponse.solutions.chemical;
                hasChanges |= setIfDifferent(historyItem.chemicalTitle, chemical.title, value -> historyItem.chemicalTitle = value);
                hasChanges |= setIfDifferent(historyItem.chemicalDescription, chemical.description, value -> historyItem.chemicalDescription = value);
                hasChanges |= setIfDifferent(historyItem.chemicalIcon, chemical.icon, value -> historyItem.chemicalIcon = value);
            }
            if (diagnosisResponse.solutions.biological != null) {
                DiagnosisResponse.Solution biological = diagnosisResponse.solutions.biological;
                hasChanges |= setIfDifferent(historyItem.biologicalTitle, biological.title, value -> historyItem.biologicalTitle = value);
                hasChanges |= setIfDifferent(historyItem.biologicalDescription, biological.description, value -> historyItem.biologicalDescription = value);
                hasChanges |= setIfDifferent(historyItem.biologicalIcon, biological.icon, value -> historyItem.biologicalIcon = value);
            }
        }

        return hasChanges;
    }

    // Dựng lại đối tượng chẩn đoán từ dữ liệu lịch sử đã lưu khi object gốc không còn tồn tại.
    @Nullable
    public static DiagnosisResponse buildDiagnosisResponseFromHistory(@Nullable DiagnosisHistoryItem historyItem) {
        if (historyItem == null) {
            return null;
        }

        boolean hasPrediction = !TextUtils.isEmpty(historyItem.predictionName)
                || !TextUtils.isEmpty(historyItem.plantTypeRaw)
                || Double.compare(historyItem.confidence, 0d) != 0
                || historyItem.healthy;
        boolean hasDisease = !TextUtils.isEmpty(historyItem.diseaseName)
                || !TextUtils.isEmpty(historyItem.symptoms)
                || !TextUtils.isEmpty(historyItem.causes)
                || !TextUtils.isEmpty(historyItem.severityLabel)
                || !TextUtils.isEmpty(historyItem.severityLevel)
                || !TextUtils.isEmpty(historyItem.severityColor)
                || !TextUtils.isEmpty(historyItem.severityIcon);
        boolean hasSolutions = !TextUtils.isEmpty(historyItem.chemicalTitle)
                || !TextUtils.isEmpty(historyItem.chemicalDescription)
                || !TextUtils.isEmpty(historyItem.chemicalIcon)
                || !TextUtils.isEmpty(historyItem.biologicalTitle)
                || !TextUtils.isEmpty(historyItem.biologicalDescription)
                || !TextUtils.isEmpty(historyItem.biologicalIcon);
        boolean hasAnyData = !TextUtils.isEmpty(historyItem.message) || hasPrediction || hasDisease || hasSolutions;
        if (!hasAnyData) {
            return null;
        }

        DiagnosisResponse diagnosisResponse = new DiagnosisResponse();
        diagnosisResponse.success = true;
        diagnosisResponse.message = historyItem.message;

        if (hasPrediction) {
            DiagnosisResponse.Prediction prediction = new DiagnosisResponse.Prediction();
            prediction.classId = historyItem.predictionClassId;
            prediction.className = historyItem.predictionName;
            prediction.plantType = historyItem.plantTypeRaw;
            prediction.confidence = historyItem.confidence;
            prediction.isHealthy = historyItem.healthy;
            diagnosisResponse.prediction = prediction;
        }

        if (hasDisease) {
            DiagnosisResponse.Disease disease = new DiagnosisResponse.Disease();
            disease.name = historyItem.diseaseName;
            disease.symptoms = historyItem.symptoms;
            disease.causes = historyItem.causes;

            if (!TextUtils.isEmpty(historyItem.severityLevel)
                    || !TextUtils.isEmpty(historyItem.severityLabel)
                    || !TextUtils.isEmpty(historyItem.severityColor)
                    || !TextUtils.isEmpty(historyItem.severityIcon)) {
                DiagnosisResponse.Severity severity = new DiagnosisResponse.Severity();
                severity.level = historyItem.severityLevel;
                severity.label = historyItem.severityLabel;
                severity.color = historyItem.severityColor;
                severity.icon = historyItem.severityIcon;
                disease.severity = severity;
            }
            diagnosisResponse.disease = disease;
        }

        if (hasSolutions) {
            DiagnosisResponse.Solutions solutions = new DiagnosisResponse.Solutions();
            if (!TextUtils.isEmpty(historyItem.chemicalTitle)
                    || !TextUtils.isEmpty(historyItem.chemicalDescription)
                    || !TextUtils.isEmpty(historyItem.chemicalIcon)) {
                DiagnosisResponse.Solution chemical = new DiagnosisResponse.Solution();
                chemical.title = historyItem.chemicalTitle;
                chemical.description = historyItem.chemicalDescription;
                chemical.icon = historyItem.chemicalIcon;
                solutions.chemical = chemical;
            }
            if (!TextUtils.isEmpty(historyItem.biologicalTitle)
                    || !TextUtils.isEmpty(historyItem.biologicalDescription)
                    || !TextUtils.isEmpty(historyItem.biologicalIcon)) {
                DiagnosisResponse.Solution biological = new DiagnosisResponse.Solution();
                biological.title = historyItem.biologicalTitle;
                biological.description = historyItem.biologicalDescription;
                biological.icon = historyItem.biologicalIcon;
                solutions.biological = biological;
            }
            diagnosisResponse.solutions = solutions;
        }

        return diagnosisResponse;
    }

    private interface StringSetter {
        void set(String value);
    }

    // Chỉ cập nhật chuỗi khi dữ liệu thay đổi để tránh ghi lại SharedPreferences không cần thiết.
    private static boolean setIfDifferent(@Nullable String currentValue,
                                          @Nullable String newValue,
                                          @NonNull StringSetter setter) {
        if (areEqual(currentValue, newValue)) {
            return false;
        }
        setter.set(newValue);
        return true;
    }

    private static boolean areEqual(@Nullable String firstValue, @Nullable String secondValue) {
        if (firstValue == null) {
            return secondValue == null;
        }
        return firstValue.equals(secondValue);
    }

    private static void saveHistoryItems(@NonNull Context context, @NonNull List<DiagnosisHistoryItem> historyItems) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sharedPreferences.edit().putString(KEY_HISTORY, GSON.toJson(historyItems)).apply();
    }
}

