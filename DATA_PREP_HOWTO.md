# Mat Than Nong — Hướng dẫn chuẩn bị dữ liệu (dễ hiểu)

## Bạn cần gì?

- Python 3.10+ đã cài (`python --version`)
- Thư mục dự án: `Coffee_Durian_AI`
- Dữ liệu gốc đặt sẵn: `RAW_DATA_CLEAN` và `RAW_DATA_FIELD` (đúng như hiện tại)

## Một lệnh duy nhất

Mở **Command Prompt** hoặc **PowerShell**, vào thư mục dự án, gõ:

```bat
cd đường_dẫn_tới\Coffee_Durian_AI
pip install -r requirements.txt
python setup_all_data.py
```

Xong. Script sẽ tạo:

| Thư mục / file | Ý nghĩa |
|----------------|---------|
| `datasets/leaf_data/train/images` | Ảnh train |
| `datasets/leaf_data/train/labels` | Nhãn YOLO (`.txt`) |
| `datasets/leaf_data/val/images` | Ảnh validation |
| `datasets/leaf_data/val/labels` | Nhãn validation |
| `datasets/leaf_data/data.yaml` | File cấu hình cho YOLOv8 |
| `datasets/leaf_data/summary_report.txt` | Báo cáo số lượng, nguồn dữ liệu |
| `runs/data_verification/` | 10 ảnh mẫu có khung để kiểm tra nhanh |

### Chỉ xem trước (không ghi file)

```bat
python setup_all_data.py --dry-run
```

### Chỉ vẽ lại ảnh kiểm tra (sau khi đã chạy xong lần đầu)

```bat
python setup_all_data.py --only-visualize
```

## Huấn luyện model (gợi ý)

Trong code train (Ultralytics), trỏ `data=` tới:

```text
datasets/leaf_data/data.yaml
```

**Lưu ý:** Dataset thống nhất có **6 lớp** (không có lớp `garbage` trong nhãn). Thư mục `garbage` được dùng làm **ảnh nền** (file `.txt` rỗng) để giảm báo động giả.

## Chỉnh tỷ lệ train/val

Sửa file `config_unified.yaml` → mục `split:` (`train` / `val` phải cộng = 1.0).

## Tránh nhân đôi dữ liệu (CLEAN và FIELD giống nhau)

Trong `config_unified.yaml`, mục `ingest:` — đặt `use_raw_clean: false` hoặc `use_raw_field: false` nếu hai thư mục trùng nội dung (ví dụ chỉ giữ một bản Durian/garbage).

## File cấu hình liên quan

- `config_unified.yaml` — toàn bộ mapping lớp, đường dẫn, RoCoLE, Durian, Coffee CSV
- `config_v1.yaml` / `config_v2.yaml` — có thêm mục `mat_than_nong` trỏ về pipeline thống nhất

## Cân bằng dataset (giảm garbage, oversample lớp thiểu số)

Sau khi có `datasets/leaf_data`, chạy:

```bat
python rebalance_dataset.py --dry-run
python rebalance_dataset.py
```

Kết quả: `datasets/leaf_data_balanced/`, `rebalance_report.txt`, ảnh mẫu trong `runs/data_verification_balanced/`.

---

*Nếu gặp lỗi chữ tiếng Việt trên Windows, dùng Windows Terminal hoặc bật UTF-8 cho console; script đã cố gắng cấu hình UTF-8 tự động.*
