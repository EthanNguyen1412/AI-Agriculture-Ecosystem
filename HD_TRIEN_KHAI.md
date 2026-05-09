# HUONG DAN TRIEN KHAI (CHO THE ANH)

Tai lieu nay de chay train model YOLOv8 bang lenh copy-paste.

## 1) Cai Python (neu may chua co)

Tai Python tai trang chinh thuc:
- [https://www.python.org/downloads/windows/](https://www.python.org/downloads/windows/)

Cach cai tren Windows:
1. Mo file cai dat Python vua tai.
2. Tick vao o **Add Python to PATH**.
3. Bam **Install Now**.
4. Mo lai PowerShell, kiem tra:

```powershell
python --version
pip --version
```

Neu hien version la OK.

## 2) Cai dat moi truong

Mo PowerShell tai thu muc du an `C:\Coffee_Durian_AI`, sau do chay:

```powershell
pip install ultralytics
```

Sau do cai toan bo thu vien trong `requirements.txt`:

```powershell
pip install -r requirements.txt
```

## 3) Chay huan luyen

```powershell
python train_final.py
```

Script se:
- Doc `config_final.yaml`
- Train voi model `yolov8n.pt`
- Tu dong fallback sang CPU neu khong co GPU
- Tu dong export `best.pt` sang `.tflite` va `.onnx`

## 4) Xem ket qua sau khi train

File trong thu muc:

```text
runs/detect/train_final/weights
```

Trong do co:
- `best.pt` (model tot nhat)
- `last.pt` (checkpoint cuoi)
- file `.tflite` va `.onnx` (sau khi export)

## 5) Kiem tra train thanh cong

Vao thu muc:

```text
runs/detect/train_final
```

Kiem tra:
- `results.png` (bieu do loss/mAP)
- `confusion_matrix.png`
- `PR_curve.png`

Neu train tot, mAP tang dan va loss giam dan theo epoch.

## 6) Xu ly loi thuong gap

- **CUDA out of memory**
  - Giam `batch` trong `config_final.yaml` (16 -> 8 -> 4)
  - Giam `imgsz` (640 -> 512)
- **Khong tim thay du lieu**
  - Kiem tra duong dan: `datasets/leaf_data_balanced`
- **Khong export duoc**
  - Thu cap nhat ultralytics: `pip install -U ultralytics`

---

Neu can ban de deploy mobile:
- Android/TFLite: dung file `.tflite`
- ONNX Runtime: dung file `.onnx`
