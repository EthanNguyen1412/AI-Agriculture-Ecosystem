package com.example.drplantadmin.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.example.drplantadmin.Models.Diagnosis;
import com.example.drplantadmin.Models.Disease;
import com.example.drplantadmin.Models.Plant;
import com.example.drplantadmin.Models.Region;
import com.example.drplantadmin.Models.Tree;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Kho dữ liệu cục bộ (SharedPreferences + Gson) cho vùng, cây, chẩn đoán và bệnh.
 * Có dữ liệu mẫu ở lần chạy đầu tiên.
 */
public final class AdminRepository {

    public static final int STATUS_NONE = 0;
    public static final int STATUS_HEALTHY = 1;
    public static final int STATUS_SICK = 2;

    private static final String PREFS = "drplant_admin_prefs";
    private static final String K_REGIONS = "regions";
    private static final String K_TREES = "trees";
    private static final String K_DIAG = "diagnoses";
    private static final String K_DISEASES = "diseases";
    private static final long DAY = 86400000L;

    private static AdminRepository instance;

    public static synchronized AdminRepository get(Context context) {
        if (instance == null) {
            instance = new AdminRepository(context.getApplicationContext());
        }
        return instance;
    }

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();
    private List<Region> regions;
    private List<Tree> trees;
    private List<Diagnosis> diagnoses;
    private List<Disease> diseases;

    private AdminRepository(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.contains(K_REGIONS)) {
            load();
        } else {
            resetSampleData();
        }
    }

    // ------------------------------------------------------------------ lưu / đọc

    private void load() {
        regions = read(K_REGIONS, new TypeToken<List<Region>>() { }.getType());
        trees = read(K_TREES, new TypeToken<List<Tree>>() { }.getType());
        diagnoses = read(K_DIAG, new TypeToken<List<Diagnosis>>() { }.getType());
        diseases = read(K_DISEASES, new TypeToken<List<Disease>>() { }.getType());
    }

    private <T> List<T> read(String key, Type type) {
        String json = prefs.getString(key, "");
        if (TextUtils.isEmpty(json)) {
            return new ArrayList<>();
        }
        List<T> list = gson.fromJson(json, type);
        return list == null ? new ArrayList<>() : list;
    }

    private void save() {
        prefs.edit()
                .putString(K_REGIONS, gson.toJson(regions))
                .putString(K_TREES, gson.toJson(trees))
                .putString(K_DIAG, gson.toJson(diagnoses))
                .putString(K_DISEASES, gson.toJson(diseases))
                .apply();
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }

    // ------------------------------------------------------------------ vùng

    public List<Region> getRegions() {
        List<Region> out = new ArrayList<>(regions);
        Collections.sort(out, (a, b) -> a.name.compareToIgnoreCase(b.name));
        return out;
    }

    public Region getRegion(String id) {
        for (Region r : regions) {
            if (r.id.equals(id)) return r;
        }
        return null;
    }

    public Region addRegion(String name, String location, String plant) {
        Region r = new Region();
        r.id = newId();
        r.name = name;
        r.location = location;
        r.plant = plant;
        regions.add(r);
        save();
        return r;
    }

    public void updateRegion(String id, String name, String location, String plant) {
        Region r = getRegion(id);
        if (r == null) return;
        r.name = name;
        r.location = location;
        r.plant = plant;
        save();
    }

    /** Xóa vùng kèm toàn bộ cây và lịch sử chẩn đoán của vùng. */
    public void deleteRegion(String id) {
        List<String> treeIds = new ArrayList<>();
        for (Tree t : trees) {
            if (t.regionId.equals(id)) treeIds.add(t.id);
        }
        for (String treeId : treeIds) {
            removeTreeInternal(treeId);
        }
        for (Iterator<Region> it = regions.iterator(); it.hasNext(); ) {
            if (it.next().id.equals(id)) it.remove();
        }
        save();
    }

    // ------------------------------------------------------------------ cây (số gốc)

    /** regionId = null: lấy tất cả cây. */
    public List<Tree> getTrees(String regionId) {
        List<Tree> out = new ArrayList<>();
        for (Tree t : trees) {
            if (regionId == null || t.regionId.equals(regionId)) out.add(t);
        }
        Collections.sort(out, (a, b) -> a.code.compareToIgnoreCase(b.code));
        return out;
    }

    public Tree getTree(String id) {
        for (Tree t : trees) {
            if (t.id.equals(id)) return t;
        }
        return null;
    }

    public boolean isCodeTaken(String code, String excludeTreeId) {
        for (Tree t : trees) {
            if (t.code.equalsIgnoreCase(code) && !t.id.equals(excludeTreeId)) return true;
        }
        return false;
    }

    /** Số thứ tự kế tiếp cho tiền tố (CF/SR). */
    public int nextNumber(String prefix) {
        int max = 0;
        String p = prefix + "-";
        for (Tree t : trees) {
            String c = t.code.toUpperCase(Locale.ROOT);
            if (c.startsWith(p)) {
                try {
                    max = Math.max(max, Integer.parseInt(c.substring(p.length())));
                } catch (NumberFormatException ignored) {
                    // mã nhập tay không theo định dạng, bỏ qua
                }
            }
        }
        return max + 1;
    }

    public static String formatCode(String prefix, int number) {
        return String.format(Locale.US, "%s-%03d", prefix, number);
    }

    public String nextCode(String plant) {
        String prefix = Plant.prefix(plant);
        return formatCode(prefix, nextNumber(prefix));
    }

    /** code rỗng = tự sinh. Trả về null nếu mã đã tồn tại. */
    public Tree addTree(String regionId, String code, String plant) {
        String finalCode = TextUtils.isEmpty(code) ? nextCode(plant) : code.toUpperCase(Locale.ROOT);
        if (isCodeTaken(finalCode, null)) return null;
        Tree t = new Tree();
        t.id = newId();
        t.code = finalCode;
        t.regionId = regionId;
        t.plant = plant;
        t.createdAt = System.currentTimeMillis();
        trees.add(t);
        save();
        return t;
    }

    /** Thêm hàng loạt: ví dụ CF-013 → CF-062. */
    public int addTrees(String regionId, String plant, int count) {
        String prefix = Plant.prefix(plant);
        int start = nextNumber(prefix);
        long now = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            Tree t = new Tree();
            t.id = newId();
            t.code = formatCode(prefix, start + i);
            t.regionId = regionId;
            t.plant = plant;
            t.createdAt = now;
            trees.add(t);
        }
        save();
        return count;
    }

    public boolean updateTree(String id, String code, String plant) {
        Tree t = getTree(id);
        if (t == null) return false;
        String finalCode = code.toUpperCase(Locale.ROOT);
        if (isCodeTaken(finalCode, id)) return false;
        t.code = finalCode;
        t.plant = plant;
        save();
        return true;
    }

    public void moveTree(String id, String newRegionId) {
        Tree t = getTree(id);
        if (t == null) return;
        t.regionId = newRegionId;
        save();
    }

    public void deleteTree(String id) {
        removeTreeInternal(id);
        save();
    }

    private void removeTreeInternal(String treeId) {
        for (Iterator<Tree> it = trees.iterator(); it.hasNext(); ) {
            if (it.next().id.equals(treeId)) it.remove();
        }
        for (Iterator<Diagnosis> it = diagnoses.iterator(); it.hasNext(); ) {
            if (it.next().treeId.equals(treeId)) it.remove();
        }
    }

    // ------------------------------------------------------------------ chẩn đoán

    /** Lịch sử chẩn đoán của một cây, mới nhất trước. */
    public List<Diagnosis> getDiagnoses(String treeId) {
        List<Diagnosis> out = new ArrayList<>();
        for (Diagnosis d : diagnoses) {
            if (d.treeId.equals(treeId)) out.add(d);
        }
        Collections.sort(out, (a, b) -> Long.compare(b.time, a.time));
        return out;
    }

    public Diagnosis latest(String treeId) {
        Diagnosis best = null;
        for (Diagnosis d : diagnoses) {
            if (d.treeId.equals(treeId) && (best == null || d.time > best.time)) best = d;
        }
        return best;
    }

    /** Tình trạng cây = kết quả chẩn đoán mới nhất. */
    public int statusOf(String treeId) {
        Diagnosis d = latest(treeId);
        if (d == null) return STATUS_NONE;
        return d.diseaseId == null ? STATUS_HEALTHY : STATUS_SICK;
    }

    /** disease = null nghĩa là khỏe mạnh. */
    public Diagnosis addDiagnosis(String treeId, Disease disease, String imagePath) {
        Diagnosis d = new Diagnosis();
        d.id = newId();
        d.treeId = treeId;
        d.time = System.currentTimeMillis();
        d.diseaseId = disease == null ? null : disease.id;
        d.diseaseName = disease == null ? null : disease.name;
        d.imagePath = imagePath;
        diagnoses.add(d);
        save();
        return d;
    }

    /** {tổng, bệnh, khỏe, chưa chẩn đoán}. regionId = null: toàn hệ thống. */
    public int[] stats(String regionId) {
        int total = 0, sick = 0, healthy = 0, none = 0;
        for (Tree t : trees) {
            if (regionId != null && !t.regionId.equals(regionId)) continue;
            total++;
            int s = statusOf(t.id);
            if (s == STATUS_SICK) sick++;
            else if (s == STATUS_HEALTHY) healthy++;
            else none++;
        }
        return new int[]{total, sick, healthy, none};
    }

    // ------------------------------------------------------------------ bệnh

    public List<Disease> getDiseases() {
        List<Disease> out = new ArrayList<>(diseases);
        Collections.sort(out, (a, b) -> a.name.compareToIgnoreCase(b.name));
        return out;
    }

    public Disease getDisease(String id) {
        if (id == null) return null;
        for (Disease d : diseases) {
            if (d.id.equals(id)) return d;
        }
        return null;
    }

    public Disease addDisease(String name, String plant, String symptoms, String causes,
                              String prevention, String treatment) {
        Disease d = new Disease();
        d.id = newId();
        d.builtIn = false;
        fill(d, name, plant, symptoms, causes, prevention, treatment);
        diseases.add(d);
        save();
        return d;
    }

    public void updateDisease(String id, String name, String plant, String symptoms,
                              String causes, String prevention, String treatment) {
        Disease d = getDisease(id);
        if (d == null) return;
        fill(d, name, plant, symptoms, causes, prevention, treatment);
        save();
    }

    /** Chỉ xóa được bệnh do admin thêm. */
    public boolean deleteDisease(String id) {
        Disease d = getDisease(id);
        if (d == null || d.builtIn) return false;
        diseases.remove(d);
        save();
        return true;
    }

    private static void fill(Disease d, String name, String plant, String symptoms,
                             String causes, String prevention, String treatment) {
        d.name = name;
        d.plant = plant;
        d.symptoms = symptoms;
        d.causes = causes;
        d.prevention = prevention;
        d.treatment = treatment;
    }

    // ------------------------------------------------------------------ dữ liệu mẫu

    /** Xóa dữ liệu hiện tại và nạp lại dữ liệu mẫu (dùng để test). */
    public void resetSampleData() {
        regions = new ArrayList<>();
        trees = new ArrayList<>();
        diagnoses = new ArrayList<>();
        diseases = new ArrayList<>();

        diseases.add(builtIn("cafe_gisat", "Cà phê bị Gỉ sắt", Plant.COFFEE,
                "Mặt dưới lá có lớp bột màu vàng cam như gỉ sắt, sờ vào dính tay. Ban đầu chỉ là chấm vàng nhạt, sau lan rộng làm rụng lá.",
                "Do nấm Hemileia tấn công. Nấm này ưa nước và chỗ tối, nên vườn nào rậm rạp, mùa mưa dầm là nó bùng phát dữ lắm.",
                "Trồng các giống kháng bệnh như Catimor, TR4. Tỉa cành tăm, cành vô hiệu cho nắng rọi vào thân, gió lùa thông thoáng.",
                "Phun ướt đẫm mặt dưới lá (chỗ nấm trốn). Dùng thuốc gốc Đồng (Cốc 85), Anvil (Hexaconazole) hoặc Azoxystrobin phun chặn đứng."));
        diseases.add(builtIn("cafe_dommatcua", "Cà phê bị Đốm mắt cua", Plant.COFFEE,
                "Vết bệnh hình tròn, ở giữa xám trắng khô xác, xung quanh có viền nâu đỏ đậm nhìn y như con mắt cua.",
                "Thường do cây \"đói ăn\", thiếu dinh dưỡng (nhất là Kali) hoặc đất cằn cỗi. Vườn ươm mà nắng quá gắt cũng dễ bị.",
                "Bón phân cân đối N-P-K, nhớ bổ sung thêm Kali để lá dày dặn. Che mát hợp lý cho cây con trong vườn ươm.",
                "Dùng các thuốc có hoạt chất Hexaconazole, Carbendazim hoặc gốc Đồng để làm khô vết bệnh. Kết hợp bón phân phục hồi cây."));
        diseases.add(builtIn("saurieng_chayla", "Sầu riêng bị Cháy lá / Thán thư", Plant.DURIAN,
                "Lá bị khô cháy từ chóp hoặc mép lá vào. Vết cháy loang lổ, chỗ tiếp giáp phần xanh và khô thường có viền vàng hoặc nâu sẫm.",
                "Do nấm tấn công khi mưa nhiều, độ ẩm cao. Vườn không thoát nước tốt, rễ bị ngộp cũng làm lá yếu dễ nhiễm bệnh.",
                "Xẻ rãnh thoát nước ngay, đừng để vườn đọng vũng. Cắt tỉa cành sát mặt đất (cách 1m) để nấm dưới đất không bắn lên lá.",
                "Cắt bỏ cành lá bệnh nặng đem đốt. Phun các hoạt chất: Mancozeb, Metalaxyl, Azoxystrobin hoặc thuốc gốc Đồng (trừ lúc ra hoa)."));
        diseases.add(builtIn("saurieng_domtao", "Sầu riêng bị Đốm tảo", Plant.DURIAN,
                "Trên mặt lá già nổi lên mấy đốm tròn màu đỏ gạch hoặc nâu cam, sờ tay vào thấy mịn mịn như lớp nhung.",
                "Do tảo ký sinh (không phải nấm). Bệnh này hay gặp ở vườn già, cây suy yếu, rậm rạp hoặc lâu ngày không rửa vườn.",
                "Sau thu hoạch nhớ \"rửa vườn\" sạch sẽ. Tỉa cành tạo tán cho ánh sáng lọt vào trong thân để diệt bớt tảo.",
                "Đặc trị bằng thuốc gốc Đồng (như Cốc 85, Champion). Phun ướt đều tán lá và thân cành để tẩy rửa rong rêu, nấm tảo."));

        Region r1 = sampleRegion("Vườn cà phê Buôn Ma Thuột", "Buôn Ma Thuột, Đắk Lắk", Plant.COFFEE);
        Region r2 = sampleRegion("Vườn sầu riêng Cai Lậy", "Cai Lậy, Tiền Giang", Plant.DURIAN);
        Region r3 = sampleRegion("Vườn cà phê Pleiku", "Pleiku, Gia Lai", Plant.COFFEE);

        int k = 0;
        k = sampleTrees(r1, 8, k);
        k = sampleTrees(r2, 6, k);
        sampleTrees(r3, 4, k);
        save();
    }

    private Disease builtIn(String id, String name, String plant, String symptoms,
                            String causes, String prevention, String treatment) {
        Disease d = new Disease();
        d.id = id;
        d.builtIn = true;
        fill(d, name, plant, symptoms, causes, prevention, treatment);
        return d;
    }

    private Region sampleRegion(String name, String location, String plant) {
        Region r = new Region();
        r.id = newId();
        r.name = name;
        r.location = location;
        r.plant = plant;
        regions.add(r);
        return r;
    }

    private int sampleTrees(Region region, int count, int k) {
        String prefix = Plant.prefix(region.plant);
        boolean coffee = !Plant.DURIAN.equals(region.plant);
        long now = System.currentTimeMillis();
        for (int i = 0; i < count; i++, k++) {
            Tree t = new Tree();
            t.id = newId();
            t.code = formatCode(prefix, nextNumber(prefix));
            t.regionId = region.id;
            t.plant = region.plant;
            t.createdAt = now - (30 + k) * DAY;
            trees.add(t);

            if (k % 5 == 4) continue; // vài cây chưa chẩn đoán lần nào
            if (k % 2 == 0) { // bản ghi cũ: khỏe
                sampleDiagnosis(t, null, now - 9 * DAY);
            }
            long when = now - (k % 6 + 1) * DAY;
            if (k % 3 == 0) {
                String diseaseId = coffee
                        ? (k % 2 == 0 ? "cafe_gisat" : "cafe_dommatcua")
                        : (k % 2 == 0 ? "saurieng_chayla" : "saurieng_domtao");
                sampleDiagnosis(t, getDisease(diseaseId), when);
            } else {
                sampleDiagnosis(t, null, when);
            }
        }
        return k;
    }

    private void sampleDiagnosis(Tree t, Disease disease, long time) {
        Diagnosis d = new Diagnosis();
        d.id = newId();
        d.treeId = t.id;
        d.time = time;
        d.diseaseId = disease == null ? null : disease.id;
        d.diseaseName = disease == null ? null : disease.name;
        diagnoses.add(d);
    }
}
