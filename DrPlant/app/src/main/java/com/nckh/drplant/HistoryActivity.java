package com.nckh.drplant;

import android.app.DatePickerDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.nckh.drplant.Models.DiagnosisHistoryItem;
import com.nckh.drplant.Models.DiagnosisResponse;
import com.nckh.drplant.Utils.DiagnosisHelper;
import com.nckh.drplant.Utils.DiagnosisHistoryManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class HistoryActivity extends AppCompatActivity {

    private static final String TAG = "HistoryActivity";
    private static final int THUMBNAIL_REQUIRED_WIDTH = 240;
    private static final int THUMBNAIL_REQUIRED_HEIGHT = 240;

    private final List<DiagnosisHistoryItem> allHistoryItems = new ArrayList<>();
    private final List<DiagnosisHistoryItem> visibleHistoryItems = new ArrayList<>();
    private final Set<String> selectedHistoryIds = new HashSet<>();
    private final SimpleDateFormat selectedDateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

    private RadioGroup filterGroup;
    private TextView emptyHistoryText;
    private TextView selectedDateFilterText;
    private HistoryAdapter historyAdapter;
    private Button pickDateButton;
    private Button clearDateButton;
    private Button multiSelectButton;
    private View selectionActionsLayout;
    private TextView selectionCountText;
    private Button selectAllButton;
    private Button deleteSelectedButton;
    private boolean selectionModeEnabled;
    private Long selectedDateMillis;

    // Khởi tạo màn hình lịch sử, ánh xạ view và gán sự kiện cho bộ lọc cùng danh sách.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_history);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Button backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> finish());

        filterGroup = findViewById(R.id.filterGroup);
        ListView historyListView = findViewById(R.id.historyListView);
        emptyHistoryText = findViewById(R.id.emptyHistoryText);
        selectedDateFilterText = findViewById(R.id.selectedDateFilterText);
        pickDateButton = findViewById(R.id.pickDateButton);
        clearDateButton = findViewById(R.id.clearDateButton);
        multiSelectButton = findViewById(R.id.multiSelectButton);
        selectionActionsLayout = findViewById(R.id.selectionActionsLayout);
        selectionCountText = findViewById(R.id.selectionCountText);
        selectAllButton = findViewById(R.id.selectAllButton);
        deleteSelectedButton = findViewById(R.id.deleteSelectedButton);
        historyListView.setEmptyView(emptyHistoryText);

        historyAdapter = new HistoryAdapter();
        historyListView.setAdapter(historyAdapter);
        historyListView.setOnItemClickListener((parent, view, position, id) -> handleHistoryItemClick(position));
        historyListView.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= visibleHistoryItems.size()) {
                return false;
            }
            if (!selectionModeEnabled) {
                enterSelectionMode();
            }
            toggleHistoryItemSelection(visibleHistoryItems.get(position));
            return true;
        });

        multiSelectButton.setOnClickListener(v -> toggleSelectionMode());
        selectAllButton.setOnClickListener(v -> toggleSelectAllVisibleItems());
        deleteSelectedButton.setOnClickListener(v -> confirmDeleteSelectedItems());
        pickDateButton.setOnClickListener(v -> showDatePickerDialog());
        clearDateButton.setOnClickListener(v -> clearDateFilter());

        filterGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (selectionModeEnabled) {
                exitSelectionMode();
            }
            applySelectedFilter();
        });
        updateDateFilterUi();
        updateSelectionUi();
    }

    // Tải lại dữ liệu lịch sử mỗi khi quay lại màn hình để đảm bảo danh sách luôn mới nhất.
    @Override
    protected void onResume() {
        super.onResume();
        loadHistory();
    }

    // Lấy toàn bộ lịch sử chẩn đoán đã lưu và áp dụng bộ lọc hiện tại.
    private void loadHistory() {
        allHistoryItems.clear();
        allHistoryItems.addAll(DiagnosisHistoryManager.getHistoryItems(this));
        applySelectedFilter();
    }

    // Lọc danh sách theo lựa chọn hiện tại: loại cây và ngày đã chọn, sau đó cập nhật giao diện.
    private void applySelectedFilter() {
        String filter = DiagnosisHistoryManager.FILTER_ALL;
        int checkedId = filterGroup.getCheckedRadioButtonId();
        if (checkedId == R.id.filterDurianButton) {
            filter = DiagnosisHistoryManager.FILTER_DURIAN;
        } else if (checkedId == R.id.filterCoffeeButton) {
            filter = DiagnosisHistoryManager.FILTER_COFFEE;
        }

        visibleHistoryItems.clear();
        visibleHistoryItems.addAll(DiagnosisHistoryManager.filterHistory(allHistoryItems, filter, selectedDateMillis));
        updateEmptyStateText(filter);
        updateDateFilterUi();
        syncSelectionWithCurrentData();
        updateSelectionUi();
        historyAdapter.notifyDataSetChanged();
    }

    // Xử lý thao tác chạm vào một mục: mở chi tiết hoặc chọn bỏ chọn khi đang ở chế độ chọn nhiều.
    private void handleHistoryItemClick(int position) {
        if (position < 0 || position >= visibleHistoryItems.size()) {
            return;
        }

        if (selectionModeEnabled) {
            toggleHistoryItemSelection(visibleHistoryItems.get(position));
            return;
        }
        openHistoryItem(position);
    }

    // Mở lại màn hình chi tiết chẩn đoán khi người dùng chọn một phần tử trong lịch sử.
    private void openHistoryItem(int position) {
        if (position < 0 || position >= visibleHistoryItems.size()) {
            return;
        }

        openHistoryItem(visibleHistoryItems.get(position));
    }

    // Mở chi tiết chẩn đoán từ dữ liệu đã lưu; nếu thiếu object gốc thì dựng lại từ thông tin lịch sử.
    private void openHistoryItem(@NonNull DiagnosisHistoryItem historyItem) {
        DiagnosisResponse diagnosisResponse = historyItem.diagnosisResponse;
        if (diagnosisResponse == null) {
            diagnosisResponse = DiagnosisHistoryManager.buildDiagnosisResponseFromHistory(historyItem);
            historyItem.diagnosisResponse = diagnosisResponse;
        }

        if (diagnosisResponse == null) {
            Toast.makeText(this, R.string.history_open_detail_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        DiagnosisHelper.launchDiagnosisActivityFromHistory(this, diagnosisResponse, historyItem.imagePath);
    }

    // Mở DatePicker để người dùng chọn một ngày cụ thể cần lọc trong lịch sử chẩn đoán.
    private void showDatePickerDialog() {
        Calendar calendar = Calendar.getInstance();
        if (selectedDateMillis != null) {
            calendar.setTimeInMillis(selectedDateMillis);
        }

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> onDateFilterSelected(year, month, dayOfMonth),
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }

    // Lưu ngày đã chọn dưới dạng đầu ngày để việc lọc theo ngày luôn ổn định.
    private void onDateFilterSelected(int year, int month, int dayOfMonth) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month);
        calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        selectedDateMillis = calendar.getTimeInMillis();

        if (selectionModeEnabled) {
            exitSelectionMode();
        }
        applySelectedFilter();
    }

    // Xóa bộ lọc ngày hiện tại để hiển thị lại toàn bộ lịch sử theo loại cây đã chọn.
    private void clearDateFilter() {
        if (selectedDateMillis == null) {
            return;
        }

        selectedDateMillis = null;
        if (selectionModeEnabled) {
            exitSelectionMode();
        }
        applySelectedFilter();
    }

    // Cập nhật phần mô tả ngày đang lọc để người dùng biết phạm vi dữ liệu đang xem.
    private void updateDateFilterUi() {
        if (selectedDateMillis == null) {
            selectedDateFilterText.setText(R.string.history_date_filter_none);
            clearDateButton.setVisibility(View.GONE);
            return;
        }

        selectedDateFilterText.setText(getString(
                R.string.history_date_filter_selected,
                selectedDateFormat.format(new Date(selectedDateMillis))
        ));
        clearDateButton.setVisibility(View.VISIBLE);
    }

    // Cập nhật thông báo trống theo cả bộ lọc loại cây và bộ lọc ngày hiện tại.
    private void updateEmptyStateText(@NonNull String plantFilter) {
        if (selectedDateMillis != null) {
            emptyHistoryText.setText(R.string.history_empty_filtered);
            return;
        }

        if (DiagnosisHistoryManager.FILTER_DURIAN.equals(plantFilter)) {
            emptyHistoryText.setText(R.string.history_empty_durian);
            return;
        }
        if (DiagnosisHistoryManager.FILTER_COFFEE.equals(plantFilter)) {
            emptyHistoryText.setText(R.string.history_empty_coffee);
            return;
        }
        emptyHistoryText.setText(R.string.history_empty_all);
    }

    // Adapter dùng để hiển thị từng mục lịch sử chẩn đoán trong ListView.
    private class HistoryAdapter extends BaseAdapter {

        private final LayoutInflater layoutInflater = LayoutInflater.from(HistoryActivity.this);
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

        // Trả về số lượng phần tử lịch sử đang được hiển thị sau khi lọc.
        @Override
        public int getCount() {
            return visibleHistoryItems.size();
        }

        // Trả về phần tử lịch sử tương ứng với vị trí trong danh sách.
        @Override
        public Object getItem(int position) {
            return visibleHistoryItems.get(position);
        }

        // Trả về id tạm thời theo vị trí phần tử trong danh sách.
        @Override
        public long getItemId(int position) {
            return position;
        }

        // Tạo hoặc tái sử dụng view item để hiển thị dữ liệu của một chẩn đoán trong lịch sử.
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder viewHolder;
            if (convertView == null) {
                convertView = layoutInflater.inflate(R.layout.item_diagnosis_history, parent, false);
                viewHolder = new ViewHolder(convertView);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }

            DiagnosisHistoryItem historyItem = visibleHistoryItems.get(position);
            bindViewHolder(viewHolder, historyItem);
            return convertView;
        }

        // Gán dữ liệu từ một mục lịch sử vào các thành phần hiển thị trên item.
        private void bindViewHolder(@NonNull ViewHolder viewHolder, @NonNull DiagnosisHistoryItem historyItem) {
            String displayName = !TextUtils.isEmpty(historyItem.diseaseName)
                    ? historyItem.diseaseName
                    : formatDisplayName(historyItem.predictionName);
            viewHolder.titleText.setText(displayName);

            String plantType = formatPlantType(historyItem.plantTypeNormalized, historyItem.plantTypeRaw);
            String status = getString(historyItem.healthy ? R.string.diagnosis_status_healthy : R.string.diagnosis_status_unhealthy);
            viewHolder.subtitleText.setText(getString(R.string.history_item_subtitle_format, plantType, status));

            viewHolder.confidenceText.setText(
                    String.format(
                            Locale.getDefault(),
                            getString(R.string.history_item_confidence_format),
                            historyItem.confidence
                    )
            );
            viewHolder.timeText.setText(dateFormat.format(new Date(historyItem.viewedAt)));
            bindThumbnail(viewHolder.thumbnailImage, historyItem.imagePath);

            boolean itemSelected = isHistoryItemSelected(historyItem);
            viewHolder.itemRoot.setBackgroundColor(itemSelected ? 0xFFDCEFFE : 0xFFF9FAFB);
            viewHolder.itemRoot.setOnClickListener(v -> {
                if (selectionModeEnabled) {
                    toggleHistoryItemSelection(historyItem);
                    return;
                }
                openHistoryItem(historyItem);
            });
            viewHolder.itemRoot.setOnLongClickListener(v -> {
                if (!selectionModeEnabled) {
                    enterSelectionMode();
                }
                toggleHistoryItemSelection(historyItem);
                return true;
            });
            viewHolder.selectionCheckBox.setVisibility(selectionModeEnabled ? View.VISIBLE : View.GONE);
            viewHolder.selectionCheckBox.setChecked(itemSelected);
            viewHolder.selectionCheckBox.setOnClickListener(v -> toggleHistoryItemSelection(historyItem));
            viewHolder.deleteButton.setVisibility(selectionModeEnabled ? View.GONE : View.VISIBLE);
            viewHolder.deleteButton.setOnClickListener(v -> confirmDeleteHistoryItem(historyItem));
        }
    }

    // ViewHolder giúp giữ tham chiếu các view con để tái sử dụng item hiệu quả hơn.
    private static class ViewHolder {
        final View itemRoot;
        final android.widget.CheckBox selectionCheckBox;
        final ImageView thumbnailImage;
        final TextView titleText;
        final TextView subtitleText;
        final TextView confidenceText;
        final TextView timeText;
        final ImageButton deleteButton;

        // Ánh xạ các thành phần hiển thị bên trong một item lịch sử.
        ViewHolder(@NonNull View rootView) {
            itemRoot = rootView.findViewById(R.id.itemRoot);
            selectionCheckBox = rootView.findViewById(R.id.selectionCheckBox);
            thumbnailImage = rootView.findViewById(R.id.thumbnailImage);
            titleText = rootView.findViewById(R.id.titleText);
            subtitleText = rootView.findViewById(R.id.subtitleText);
            confidenceText = rootView.findViewById(R.id.confidenceText);
            timeText = rootView.findViewById(R.id.timeText);
            deleteButton = rootView.findViewById(R.id.deleteButton);
        }
    }

    // Chuyển đổi giữa chế độ xem thông thường và chế độ chọn nhiều mục trong lịch sử.
    private void toggleSelectionMode() {
        if (selectionModeEnabled) {
            exitSelectionMode();
            return;
        }
        enterSelectionMode();
    }

    // Bật chế độ chọn nhiều để người dùng có thể chọn nhiều chẩn đoán cần xóa cùng lúc.
    private void enterSelectionMode() {
        if (visibleHistoryItems.isEmpty()) {
            Toast.makeText(this, R.string.history_multi_select_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        selectionModeEnabled = true;
        selectedHistoryIds.clear();
        updateSelectionUi();
        historyAdapter.notifyDataSetChanged();
    }

    // Tắt chế độ chọn nhiều, xóa các mục đang chọn và đưa giao diện về trạng thái bình thường.
    private void exitSelectionMode() {
        selectionModeEnabled = false;
        selectedHistoryIds.clear();
        updateSelectionUi();
        historyAdapter.notifyDataSetChanged();
    }

    // Cập nhật giao diện của vùng thao tác chọn nhiều như số lượng đã chọn và trạng thái nút.
    private void updateSelectionUi() {
        selectionActionsLayout.setVisibility(selectionModeEnabled ? View.VISIBLE : View.GONE);
        multiSelectButton.setText(selectionModeEnabled ? R.string.history_multi_select_done : R.string.history_multi_select);

        int selectedCount = selectedHistoryIds.size();
        selectionCountText.setText(
                selectedCount == 0
                        ? getString(R.string.history_selection_count_zero)
                        : getString(R.string.history_selection_count_format, selectedCount)
        );

        deleteSelectedButton.setEnabled(selectedCount > 0);
        deleteSelectedButton.setAlpha(selectedCount > 0 ? 1f : 0.5f);
        selectAllButton.setText(areAllVisibleItemsSelected() ? R.string.history_deselect_all : R.string.history_select_all);
    }

    // Đồng bộ lại danh sách id đã chọn với dữ liệu hiện có để tránh giữ các mục đã bị xóa.
    private void syncSelectionWithCurrentData() {
        if (selectedHistoryIds.isEmpty()) {
            return;
        }

        Set<String> validIds = new HashSet<>();
        for (DiagnosisHistoryItem historyItem : allHistoryItems) {
            if (historyItem != null && !TextUtils.isEmpty(historyItem.id)) {
                validIds.add(historyItem.id);
            }
        }
        selectedHistoryIds.retainAll(validIds);
    }

    // Kiểm tra một mục lịch sử hiện có đang nằm trong danh sách được chọn hay chưa.
    private boolean isHistoryItemSelected(@NonNull DiagnosisHistoryItem historyItem) {
        return !TextUtils.isEmpty(historyItem.id) && selectedHistoryIds.contains(historyItem.id);
    }

    // Chọn hoặc bỏ chọn một mục lịch sử trong chế độ chọn nhiều rồi cập nhật lại giao diện.
    private void toggleHistoryItemSelection(@NonNull DiagnosisHistoryItem historyItem) {
        if (TextUtils.isEmpty(historyItem.id)) {
            return;
        }

        if (selectedHistoryIds.contains(historyItem.id)) {
            selectedHistoryIds.remove(historyItem.id);
        } else {
            selectedHistoryIds.add(historyItem.id);
        }
        updateSelectionUi();
        historyAdapter.notifyDataSetChanged();
    }

    // Kiểm tra xem toàn bộ mục đang hiển thị theo bộ lọc hiện tại đã được chọn hết hay chưa.
    private boolean areAllVisibleItemsSelected() {
        if (visibleHistoryItems.isEmpty()) {
            return false;
        }

        for (DiagnosisHistoryItem historyItem : visibleHistoryItems) {
            if (historyItem == null || TextUtils.isEmpty(historyItem.id) || !selectedHistoryIds.contains(historyItem.id)) {
                return false;
            }
        }
        return true;
    }

    // Chọn tất cả hoặc bỏ chọn tất cả các mục đang hiển thị trong danh sách đã được lọc.
    private void toggleSelectAllVisibleItems() {
        if (visibleHistoryItems.isEmpty()) {
            return;
        }

        boolean allSelected = areAllVisibleItemsSelected();
        for (DiagnosisHistoryItem historyItem : visibleHistoryItems) {
            if (historyItem == null || TextUtils.isEmpty(historyItem.id)) {
                continue;
            }

            if (allSelected) {
                selectedHistoryIds.remove(historyItem.id);
            } else {
                selectedHistoryIds.add(historyItem.id);
            }
        }
        updateSelectionUi();
        historyAdapter.notifyDataSetChanged();
    }

    // Hiển thị hộp thoại xác nhận trước khi xóa đồng thời nhiều mục đã được chọn trong lịch sử.
    private void confirmDeleteSelectedItems() {
        int selectedCount = selectedHistoryIds.size();
        if (selectedCount == 0) {
            Toast.makeText(this, R.string.history_delete_selected_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.history_delete_selected_title)
                .setMessage(getString(R.string.history_delete_selected_message, selectedCount))
                .setNegativeButton(R.string.history_delete_cancel, null)
                .setPositiveButton(R.string.history_delete_confirm, (dialog, which) -> deleteSelectedHistoryItems())
                .show();
    }

    // Xóa toàn bộ các mục đang được chọn khỏi bộ nhớ lưu trữ và làm mới danh sách đang hiển thị.
    private void deleteSelectedHistoryItems() {
        List<String> historyIdsToDelete = new ArrayList<>(selectedHistoryIds);
        int removedCount = DiagnosisHistoryManager.removeHistoryItems(this, historyIdsToDelete);
        if (removedCount <= 0) {
            Toast.makeText(this, R.string.history_delete_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        removeHistoryItemsFromMemory(historyIdsToDelete);
        exitSelectionMode();
        applySelectedFilter();
        Toast.makeText(this, getString(R.string.history_delete_selected_success, removedCount), Toast.LENGTH_SHORT).show();
    }

    // Hiển thị hộp thoại xác nhận trước khi xóa một mục chẩn đoán khỏi lịch sử đã lưu.
    private void confirmDeleteHistoryItem(@NonNull DiagnosisHistoryItem historyItem) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.history_delete_title)
                .setMessage(R.string.history_delete_message)
                .setNegativeButton(R.string.history_delete_cancel, null)
                .setPositiveButton(R.string.history_delete_confirm, (dialog, which) -> deleteHistoryItem(historyItem))
                .show();
    }

    // Xóa mục lịch sử khỏi bộ nhớ lưu trữ và cập nhật lại danh sách đang hiển thị trên màn hình.
    private void deleteHistoryItem(@NonNull DiagnosisHistoryItem historyItem) {
        boolean removed = DiagnosisHistoryManager.removeHistoryItem(this, historyItem.id);
        if (!removed) {
            Toast.makeText(this, R.string.history_delete_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        removeHistoryItemFromMemory(historyItem.id);
        applySelectedFilter();
        Toast.makeText(this, R.string.history_delete_success, Toast.LENGTH_SHORT).show();
    }

    // Loại bỏ mục vừa xóa khỏi danh sách gốc trong bộ nhớ để tránh phải tải lại toàn bộ dữ liệu.
    private void removeHistoryItemFromMemory(String historyItemId) {
        if (TextUtils.isEmpty(historyItemId)) {
            return;
        }

        for (int index = allHistoryItems.size() - 1; index >= 0; index--) {
            DiagnosisHistoryItem item = allHistoryItems.get(index);
            if (item != null && historyItemId.equals(item.id)) {
                allHistoryItems.remove(index);
                break;
            }
        }
    }

    // Loại bỏ nhiều mục vừa xóa khỏi danh sách gốc trong bộ nhớ để giao diện cập nhật ngay lập tức.
    private void removeHistoryItemsFromMemory(@NonNull List<String> historyItemIds) {
        if (historyItemIds.isEmpty()) {
            return;
        }

        Set<String> idSet = new HashSet<>(historyItemIds);
        for (int index = allHistoryItems.size() - 1; index >= 0; index--) {
            DiagnosisHistoryItem item = allHistoryItems.get(index);
            if (item != null && idSet.contains(item.id)) {
                allHistoryItems.remove(index);
            }
        }
    }

    // Nạp ảnh thumbnail từ đường dẫn đã lưu và hiển thị ảnh mặc định nếu không đọc được file.
    private void bindThumbnail(@NonNull ImageView thumbnailImage, String imagePath) {
        Bitmap thumbnailBitmap = loadThumbnailBitmap(imagePath);
        if (thumbnailBitmap != null) {
            thumbnailImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumbnailImage.setImageBitmap(thumbnailBitmap);
            return;
        }

        thumbnailImage.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        thumbnailImage.setImageResource(android.R.drawable.ic_menu_report_image);
    }

    // Giải mã ảnh kích thước nhỏ để hiển thị trong danh sách lịch sử mà không tốn nhiều bộ nhớ.
    private Bitmap loadThumbnailBitmap(String imagePath) {
        if (TextUtils.isEmpty(imagePath)) {
            return null;
        }

        try {
            BitmapFactory.Options boundsOptions = new BitmapFactory.Options();
            boundsOptions.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(imagePath, boundsOptions);

            BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();
            bitmapOptions.inSampleSize = calculateInSampleSize(boundsOptions);
            bitmapOptions.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap decodedBitmap = BitmapFactory.decodeFile(imagePath, bitmapOptions);
            if (decodedBitmap == null) {
                return null;
            }

            return applyExifOrientation(imagePath, decodedBitmap);
        } catch (Exception exception) {
            Log.e(TAG, "Unable to decode history thumbnail", exception);
            return null;
        }
    }

    // Xoay ảnh thumbnail theo thông tin EXIF để ảnh trong lịch sử hiển thị đúng chiều.
    private Bitmap applyExifOrientation(String imagePath, Bitmap bitmap) {
        try {
            ExifInterface exifInterface = new ExifInterface(imagePath);
            int orientation = exifInterface.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
            );

            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    matrix.postRotate(90f);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    matrix.postRotate(180f);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    matrix.postRotate(270f);
                    break;
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                    matrix.preScale(-1f, 1f);
                    break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                    matrix.preScale(1f, -1f);
                    break;
                case ExifInterface.ORIENTATION_TRANSPOSE:
                    matrix.preScale(-1f, 1f);
                    matrix.postRotate(270f);
                    break;
                case ExifInterface.ORIENTATION_TRANSVERSE:
                    matrix.preScale(-1f, 1f);
                    matrix.postRotate(90f);
                    break;
                case ExifInterface.ORIENTATION_NORMAL:
                case ExifInterface.ORIENTATION_UNDEFINED:
                default:
                    return bitmap;
            }

            Bitmap rotatedBitmap = Bitmap.createBitmap(
                    bitmap,
                    0,
                    0,
                    bitmap.getWidth(),
                    bitmap.getHeight(),
                    matrix,
                    true
            );

            if (rotatedBitmap != bitmap) {
                bitmap.recycle();
            }
            return rotatedBitmap;
        } catch (Exception exception) {
            Log.e(TAG, "Unable to apply EXIF orientation to history thumbnail", exception);
            return bitmap;
        }
    }

    // Tính hệ số thu nhỏ phù hợp để ảnh danh sách hiển thị nhanh và tiết kiệm bộ nhớ hơn.
    private int calculateInSampleSize(BitmapFactory.Options options) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;

        if (height > THUMBNAIL_REQUIRED_HEIGHT || width > THUMBNAIL_REQUIRED_WIDTH) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= THUMBNAIL_REQUIRED_HEIGHT && (halfWidth / inSampleSize) >= THUMBNAIL_REQUIRED_WIDTH) {
                inSampleSize *= 2;
            }
        }

        return Math.max(inSampleSize, 1);
    }

    // Chuyển plant type đã chuẩn hóa thành nhãn hiển thị thân thiện cho người dùng.
    private String formatPlantType(String normalizedType, String rawType) {
        if (DiagnosisHistoryManager.FILTER_DURIAN.equals(normalizedType)) {
            return getString(R.string.history_filter_durian);
        }
        if (DiagnosisHistoryManager.FILTER_COFFEE.equals(normalizedType)) {
            return getString(R.string.history_filter_coffee);
        }
        if (!TextUtils.isEmpty(rawType)) {
            return formatDisplayName(rawType);
        }
        return getString(R.string.diagnosis_not_available);
    }

    // Chuẩn hóa chuỗi hiển thị bằng cách thay dấu gạch dưới thành khoảng trắng.
    private String formatDisplayName(String rawValue) {
        if (TextUtils.isEmpty(rawValue)) {
            return getString(R.string.diagnosis_not_available);
        }
        return rawValue.replace("_", " ");
    }

    // Nếu người dùng đang ở chế độ chọn nhiều thì ưu tiên thoát chế độ này trước khi rời màn hình.
    @Override
    public void onBackPressed() {
        if (selectionModeEnabled) {
            exitSelectionMode();
            return;
        }
        super.onBackPressed();
    }
}

