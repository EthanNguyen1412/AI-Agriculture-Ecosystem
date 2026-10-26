package com.example.drplantadmin;


import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.example.drplantadmin.data.AdminRepository;
import com.example.drplantadmin.Models.Disease;
import com.example.drplantadmin.Models.Plant;
import com.example.drplantadmin.Models.Region;
import com.example.drplantadmin.Models.Tree;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Các hộp thoại thêm/sửa/xóa dùng chung cho vùng, cây và bệnh. */
public final class FormDialogs {

    private FormDialogs() { }

    private static String plantOf(RadioGroup group) {
        return group.getCheckedRadioButtonId() == R.id.radioDurian ? Plant.DURIAN : Plant.COFFEE;
    }

    private static void checkPlant(RadioGroup group, String plant) {
        group.check(Plant.DURIAN.equals(plant) ? R.id.radioDurian : R.id.radioCoffee);
    }

    /** Dialog có nút Lưu chỉ đóng khi hợp lệ. validate trả về true nếu lưu thành công. */
    private static void showForm(Context ctx, String title, View view, Validator validator) {
        AlertDialog dialog = new MaterialAlertDialogBuilder(ctx)
                .setTitle(title)
                .setView(view)
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Lưu", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(b -> {
                    if (validator.save()) dialog.dismiss();
                }));
        dialog.show();
    }

    private interface Validator {
        boolean save();
    }

    // ------------------------------------------------------------------ VÙNG

    public static void regionForm(Context ctx, @Nullable Region region, Runnable onDone) {
        AdminRepository repo = AdminRepository.get(ctx);
        View v = LayoutInflater.from(ctx).inflate(R.layout.dialog_region, null);
        TextInputLayout nameLayout = v.findViewById(R.id.nameLayout);
        TextInputLayout locLayout = v.findViewById(R.id.locationLayout);
        TextInputEditText name = v.findViewById(R.id.nameInput);
        TextInputEditText loc = v.findViewById(R.id.locationInput);
        RadioGroup group = v.findViewById(R.id.plantGroup);

        if (region != null) {
            name.setText(region.name);
            loc.setText(region.location);
            checkPlant(group, region.plant);
        } else {
            checkPlant(group, Plant.COFFEE);
        }

        showForm(ctx, region == null ? "Thêm vùng" : "Sửa vùng", v, () -> {
            nameLayout.setError(null);
            locLayout.setError(null);
            String n = UiUtil.text(name);
            String l = UiUtil.text(loc);
            if (n.isEmpty()) {
                nameLayout.setError("Nhập tên vùng");
                return false;
            }
            if (l.isEmpty()) {
                locLayout.setError("Nhập địa điểm");
                return false;
            }
            if (region == null) {
                repo.addRegion(n, l, plantOf(group));
            } else {
                repo.updateRegion(region.id, n, l, plantOf(group));
            }
            if (onDone != null) onDone.run();
            return true;
        });
    }

    public static void confirmDeleteRegion(Context ctx, Region region, Runnable onDone) {
        AdminRepository repo = AdminRepository.get(ctx);
        int trees = repo.stats(region.id)[0];
        new MaterialAlertDialogBuilder(ctx)
                .setTitle("Xóa vùng?")
                .setMessage("Vùng “" + region.name + "” và " + trees
                        + " cây thuộc vùng (cùng lịch sử chẩn đoán) sẽ bị xóa vĩnh viễn.")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xóa", (d, w) -> {
                    repo.deleteRegion(region.id);
                    if (onDone != null) onDone.run();
                })
                .show();
    }

    // ------------------------------------------------------------------ CÂY

    /** tree == null: thêm cây mới vào vùng regionId; ngược lại là sửa cây. */
    public static void treeForm(Context ctx, String regionId, @Nullable Tree tree, Runnable onDone) {
        AdminRepository repo = AdminRepository.get(ctx);
        View v = LayoutInflater.from(ctx).inflate(R.layout.dialog_tree, null);
        TextInputLayout codeLayout = v.findViewById(R.id.codeLayout);
        TextInputEditText code = v.findViewById(R.id.codeInput);
        RadioGroup group = v.findViewById(R.id.plantGroup);

        if (tree != null) {
            code.setText(tree.code);
            checkPlant(group, tree.plant);
        } else {
            Region region = repo.getRegion(regionId);
            checkPlant(group, region == null ? Plant.COFFEE : region.plant);
            codeLayout.setHelperText("Gợi ý mã kế tiếp: " + repo.nextCode(plantOf(group)));
            group.setOnCheckedChangeListener((g, id) ->
                    codeLayout.setHelperText("Gợi ý mã kế tiếp: " + repo.nextCode(plantOf(group))));
        }

        showForm(ctx, tree == null ? "Thêm cây" : "Sửa cây", v, () -> {
            codeLayout.setError(null);
            String c = UiUtil.text(code).toUpperCase(Locale.ROOT);
            if (tree == null) {
                if (!c.isEmpty() && repo.isCodeTaken(c, null)) {
                    codeLayout.setError("Số gốc " + c + " đã tồn tại");
                    return false;
                }
                repo.addTree(regionId, c, plantOf(group));
            } else {
                if (c.isEmpty()) {
                    codeLayout.setError("Nhập số gốc");
                    return false;
                }
                if (!repo.updateTree(tree.id, c, plantOf(group))) {
                    codeLayout.setError("Số gốc " + c + " đã tồn tại");
                    return false;
                }
            }
            if (onDone != null) onDone.run();
            return true;
        });
    }

    /** Thêm hàng loạt, ví dụ CF-013 đến CF-062. */
    public static void bulkForm(Context ctx, String regionId, Runnable onDone) {
        AdminRepository repo = AdminRepository.get(ctx);
        View v = LayoutInflater.from(ctx).inflate(R.layout.dialog_bulk, null);
        TextInputLayout countLayout = v.findViewById(R.id.countLayout);
        TextInputEditText count = v.findViewById(R.id.countInput);
        RadioGroup group = v.findViewById(R.id.plantGroup);
        TextView preview = v.findViewById(R.id.tvPreview);

        Region region = repo.getRegion(regionId);
        checkPlant(group, region == null ? Plant.COFFEE : region.plant);

        Runnable updatePreview = () -> {
            int n = parseCount(UiUtil.text(count));
            String prefix = Plant.prefix(plantOf(group));
            int start = repo.nextNumber(prefix);
            if (n < 1 || n > 200) {
                preview.setText("");
            } else {
                preview.setText("Sẽ tạo: " + AdminRepository.formatCode(prefix, start)
                        + " → " + AdminRepository.formatCode(prefix, start + n - 1));
            }
        };
        count.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable e) { updatePreview.run(); }
        });
        group.setOnCheckedChangeListener((g, id) -> updatePreview.run());

        showForm(ctx, "Thêm cây hàng loạt", v, () -> {
            countLayout.setError(null);
            int n = parseCount(UiUtil.text(count));
            if (n < 1 || n > 200) {
                countLayout.setError("Nhập số từ 1 đến 200");
                return false;
            }
            repo.addTrees(regionId, plantOf(group), n);
            Toast.makeText(ctx, "Đã thêm " + n + " cây", Toast.LENGTH_SHORT).show();
            if (onDone != null) onDone.run();
            return true;
        });
    }

    private static int parseCount(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static void moveTree(Context ctx, Tree tree, Runnable onDone) {
        AdminRepository repo = AdminRepository.get(ctx);
        List<Region> others = new ArrayList<>();
        for (Region r : repo.getRegions()) {
            if (!r.id.equals(tree.regionId)) others.add(r);
        }
        if (others.isEmpty()) {
            Toast.makeText(ctx, "Chưa có vùng khác để chuyển", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[others.size()];
        for (int i = 0; i < names.length; i++) names[i] = others.get(i).name;
        new MaterialAlertDialogBuilder(ctx)
                .setTitle("Chuyển " + tree.code + " sang vùng")
                .setItems(names, (d, which) -> {
                    repo.moveTree(tree.id, others.get(which).id);
                    if (onDone != null) onDone.run();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    public static void confirmDeleteTree(Context ctx, Tree tree, Runnable onDone) {
        AdminRepository repo = AdminRepository.get(ctx);
        new MaterialAlertDialogBuilder(ctx)
                .setTitle("Xóa cây " + tree.code + "?")
                .setMessage("Cây và toàn bộ lịch sử chẩn đoán của cây sẽ bị xóa vĩnh viễn.")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xóa", (d, w) -> {
                    repo.deleteTree(tree.id);
                    if (onDone != null) onDone.run();
                })
                .show();
    }

    /** Chẩn đoán thử (khi chưa nối camera): chọn Khỏe mạnh hoặc một bệnh phù hợp loại cây. */
    public static void testDiagnosis(Context ctx, Tree tree, Runnable onDone) {
        AdminRepository repo = AdminRepository.get(ctx);
        List<Disease> options = new ArrayList<>();
        for (Disease d : repo.getDiseases()) {
            if (tree.plant.equals(d.plant)) options.add(d);
        }
        String[] items = new String[options.size() + 1];
        items[0] = "Khỏe mạnh";
        for (int i = 0; i < options.size(); i++) items[i + 1] = options.get(i).name;
        new MaterialAlertDialogBuilder(ctx)
                .setTitle("Kết quả chẩn đoán thử")
                .setItems(items, (d, which) -> {
                    repo.addDiagnosis(tree.id, which == 0 ? null : options.get(which - 1), null);
                    if (onDone != null) onDone.run();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    // ------------------------------------------------------------------ BỆNH

    public static void diseaseForm(Context ctx, @Nullable Disease disease, Runnable onDone) {
        AdminRepository repo = AdminRepository.get(ctx);
        View v = LayoutInflater.from(ctx).inflate(R.layout.dialog_disease, null);
        TextInputLayout nameLayout = v.findViewById(R.id.nameLayout);
        TextInputEditText name = v.findViewById(R.id.nameInput);
        TextInputEditText symptoms = v.findViewById(R.id.symptomsInput);
        TextInputEditText causes = v.findViewById(R.id.causesInput);
        TextInputEditText prevention = v.findViewById(R.id.preventionInput);
        TextInputEditText treatment = v.findViewById(R.id.treatmentInput);
        RadioGroup group = v.findViewById(R.id.plantGroup);

        if (disease != null) {
            name.setText(disease.name);
            symptoms.setText(disease.symptoms);
            causes.setText(disease.causes);
            prevention.setText(disease.prevention);
            treatment.setText(disease.treatment);
            checkPlant(group, disease.plant);
            if (disease.builtIn) { // bệnh gốc của model: không đổi loại cây
                for (int i = 0; i < group.getChildCount(); i++) group.getChildAt(i).setEnabled(false);
            }
        } else {
            checkPlant(group, Plant.COFFEE);
        }

        showForm(ctx, disease == null ? "Thêm bệnh" : "Cập nhật bệnh", v, () -> {
            nameLayout.setError(null);
            String n = UiUtil.text(name);
            if (n.isEmpty()) {
                nameLayout.setError("Nhập tên bệnh");
                return false;
            }
            if (disease == null) {
                repo.addDisease(n, plantOf(group), UiUtil.text(symptoms), UiUtil.text(causes),
                        UiUtil.text(prevention), UiUtil.text(treatment));
            } else {
                repo.updateDisease(disease.id, n, plantOf(group), UiUtil.text(symptoms),
                        UiUtil.text(causes), UiUtil.text(prevention), UiUtil.text(treatment));
            }
            if (onDone != null) onDone.run();
            return true;
        });
    }

    public static void confirmDeleteDisease(Context ctx, Disease disease, Runnable onDone) {
        AdminRepository repo = AdminRepository.get(ctx);
        new MaterialAlertDialogBuilder(ctx)
                .setTitle("Xóa bệnh?")
                .setMessage("Bệnh “" + disease.name + "” sẽ bị xóa khỏi danh sách. "
                        + "Các lần chẩn đoán cũ vẫn giữ lại tên bệnh.")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xóa", (d, w) -> {
                    repo.deleteDisease(disease.id);
                    if (onDone != null) onDone.run();
                })
                .show();
    }
}
