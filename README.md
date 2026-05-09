# AI Agriculture Ecosystem — Coffee & Durian Leaf Diagnosis

Hệ sinh thái AI phục vụ **chẩn đoán bệnh trên lá** cho **cà phê** và **sầu riêng**, gồm pipeline chuẩn bị dữ liệu, huấn luyện YOLOv8, API FastAPI, xuất ONNX/TFLite và ứng dụng Android (DrPlant).

## Phạm vi lớp (6 classes)

| STT | ID model | Mô tả ngắn |
|-----|-----------|------------|
| 1 | `cafe_gisat` | Cà phê — gỉ sắt |
| 2 | `cafe_dommatcua` | Cà phê — đốm mắt cua |
| 3 | `cafe_khoe` | Cà phê — khỏe mạnh (lá khỏe) |
| 4 | `saurieng_chayla` | Sầu riêng — cháy lá |
| 5 | `saurieng_domtao` | Sầu riêng — đốm tảo |
| 6 | `saurieng_khoe` | Sầu riêng — khỏe mạnh (lá khỏe) |

## Công nghệ chính

- **Huấn luyện:** PyTorch, [Ultralytics YOLOv8](https://github.com/ultralytics/ultralytics)
- **API:** FastAPI, Uvicorn
- **Mobile:** Android (Gradle), ONNX Runtime / TFLite trong project `DrPlant/`

## Cấu trúc thư mục (trên GitHub)

| Thư mục / file | Nội dung |
|----------------|----------|
| `train_final.py`, `train_v1_clean.py`, `train_v2_finetune.py` | Script huấn luyện |
| `config_final.yaml`, `config_*.yaml` | Cấu hình dataset & huấn luyện |
| `setup_all_data.py`, `prepare_*.py`, `rebalance_dataset.py` | Chuẩn bị và cân bằng dữ liệu |
| `eval_real_images.py`, `summarize_report.py` | Đánh giá & tổng hợp báo cáo |
| `app.py` | API chẩn đoán ảnh |
| `export_tflite.py` | Hỗ trợ xuất TFLite |
| `train_final17/` | **Kết quả huấn luyện:** biểu đồ, `results.csv`, `weights/` (`.pt`, `.onnx`, SavedModel, TFLite) |
| `exports/` | `best.tflite` (export gọn) |
| `reports/` | CSV đánh giá thực địa (`real_world_eval`, …) |
| `runs/` | Run kiểm tra dữ liệu và một số artifact validation |
| `DrPlant/` | Mã nguồn Android |
| `handbook.csv` | Tra cứu / mapping phụ trợ |
| `DATA_PREP_HOWTO.md`, `HD_TRIEN_KHAI.md` | Hướng dẫn chi tiết trong repo |

**Không đưa lên Git** (do dung lượng / tái tạo được): `datasets/`, `RAW_DATA_CLEAN/`, `RAW_DATA_FIELD/`, `.venv/`, và file zip lớn trong `exports/` — xem `.gitignore`.

## Bắt đầu nhanh

### 1. Môi trường

```powershell
cd đường_dẫn_tới\Coffee_Durian_AI
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
```

GPU NVIDIA: nếu cần PyTorch bản CUDA, cài theo hướng dẫn tại [pytorch.org](https://pytorch.org/) cho đúng phiên bản CUDA của máy.

### 2. Dữ liệu

- Có thể chạy pipeline chuẩn bị khi đã có dữ liệu gốc đúng cấu trúc (xem `DATA_PREP_HOWTO.md`):

```powershell
python setup_all_data.py
```

- File dataset cho YOLO được khai báo trong `config_final.yaml` (ví dụ `datasets/leaf_data_balanced`). Sau khi tạo dataset cục bộ, chỉnh `path` trong YAML cho khớp máy bạn.

### 3. Huấn luyện

```powershell
python train_final.py
```

Script đọc `config_final.yaml`, huấn luyện YOLOv8, tự fallback **CPU** nếu không có GPU, và thiết lập export ONNX/TFLite theo config.

Kết quả mặc định nằm dưới `runs/detect/<name>/` (theo `project`/`name` trong YAML). Trên repo này có snapshot đầy đủ tại `train_final17/`.

### 4. API

```powershell
python app.py
```

API (Ultralytics/YOLO trong `app.py`): upload ảnh để suy luận — xem docstring và endpoint trong file để biết URL/port cụ thể.

### 5. Android

Mở thư mục `DrPlant/` trong Android Studio, đồng bộ Gradle và build như project Android thông thường. Model ONNX/TFLite mẫu nằm trong `app/src/main/assets/` và `res/`.

## Tài liệu thêm

- Chuẩn bị dữ liệu: `DATA_PREP_HOWTO.md`
- Triển khai / train copy-paste: `HD_TRIEN_KHAI.md`

## Repository

- GitHub: [EthanNguyen1412/AI-Agriculture-Ecosystem](https://github.com/EthanNguyen1412/AI-Agriculture-Ecosystem)

---

*Dự án phục vụ nghiên cứu / demo chẩn đoán lá cây — điều chỉnh đường dẫn và config cho đúng môi trường cục bộ trước khi chạy.*
