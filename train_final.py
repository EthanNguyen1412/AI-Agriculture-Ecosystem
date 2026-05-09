from pathlib import Path
import traceback

import torch
import yaml
from ultralytics import YOLO


def load_config(cfg_path: Path) -> dict:
    with open(cfg_path, "r", encoding="utf-8") as f:
        return yaml.safe_load(f)


def resolve_device(preferred_device: int) -> str:
    """Tu dong fallback CPU neu khong co CUDA."""
    if torch.cuda.is_available():
        return str(preferred_device)
    return "cpu"


def print_cuda_warning_if_needed() -> None:
    """Canh bao ro rang khi dang dung torch CPU-only hoac CUDA khong kha dung."""
    torch_ver = str(torch.__version__).lower()
    cuda_ok = torch.cuda.is_available()

    # Case 1: ban torch CPU-only
    if "+cpu" in torch_ver:
        print("\n" + "!" * 70)
        print("CANH BAO: BAN DANG DUNG PYTORCH CPU-ONLY (+cpu)")
        print("=> Model se train bang CPU, khong dung duoc GPU.")
        print("Cach sua nhanh:")
        print("  pip uninstall -y torch torchvision torchaudio")
        print("  pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu128")
        print("!" * 70 + "\n")
        return

    # Case 2: torch co CUDA build nhung van khong nhan GPU
    if not cuda_ok:
        print("\n" + "!" * 70)
        print("CANH BAO: KHONG PHAT HIEN CUDA/GPU")
        print("=> Script se fallback sang CPU.")
        print("Kiem tra:")
        print("- Driver NVIDIA da cai dung chua")
        print("- Chay nvidia-smi co thay GPU khong")
        print("- Torch CUDA build co khop voi moi truong khong")
        print("!" * 70 + "\n")


def print_header(cfg_path: Path, device: str, data_yaml_path: Path) -> None:
    print("=" * 70)
    print("MAT THAN NONG - TRAIN FINAL")
    print(f"Config file     : {cfg_path}")
    print(f"Data YAML       : {data_yaml_path}")
    print(f"CUDA available  : {torch.cuda.is_available()}")
    print(f"Train device    : {device}")
    if torch.cuda.is_available():
        print(f"GPU name        : {torch.cuda.get_device_name(0)}")
    print("=" * 70)
    print()


def sanitize_output_settings(project_root: Path, train_cfg: dict) -> tuple[str, str]:
    """
    Chuan hoa project/name de tranh bi long duong dan:
    vi du name='runs/detect/train_final15' se gay nested folder.
    """
    raw_project = str(train_cfg.get("project", "runs/detect")).strip()
    raw_name = str(train_cfg.get("name", "train_final")).strip()

    project_path = Path(raw_project)
    if not project_path.is_absolute():
        project_path = (project_root / project_path).resolve()

    # name chi nen la ten thu muc con, khong chua slash/backslash
    safe_name = Path(raw_name.replace("\\", "/")).name
    if safe_name != raw_name:
        print(f"[WARN] name='{raw_name}' khong hop le. Tu dong chuyen thanh '{safe_name}'.")
    if not safe_name:
        safe_name = "train_final"

    return str(project_path), safe_name


def assert_writable_output(project_abs: str, run_name: str) -> None:
    """
    Kiem tra quyen ghi truoc khi train de bat loi PermissionError som.
    """
    run_dir = Path(project_abs) / run_name
    run_dir.mkdir(parents=True, exist_ok=True)
    probe = run_dir / ".write_test.tmp"
    with open(probe, "w", encoding="utf-8") as f:
        f.write("ok")
    probe.unlink(missing_ok=True)


def write_runtime_data_yaml(cfg: dict, out_path: Path) -> None:
    runtime_data = {
        "path": cfg["path"],
        "train": cfg["train"],
        "val": cfg["val"],
        "nc": cfg["nc"],
        "names": cfg["names"],
    }
    out_path.parent.mkdir(parents=True, exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as f:
        yaml.safe_dump(runtime_data, f, sort_keys=False, allow_unicode=True)


def export_models(best_model_path: Path, export_cfg: dict) -> None:
    """Export best.pt sang TFLite va ONNX cho mobile deployment."""
    print("\n" + "=" * 70)
    print("BAT DAU EXPORT MODEL")
    print("=" * 70)
    model = YOLO(str(best_model_path))

    if export_cfg.get("tflite", True):
        print("- Export TFLite...")
        try:
            tflite_path = model.export(
                format="tflite",
                int8=False,
                half=False,
            )
            print(f"  ✓ TFLite: {tflite_path}")
        except Exception as e:
            print(f"  ✗ Loi export TFLite: {e}")

    if export_cfg.get("onnx", True):
        print("- Export ONNX...")
        try:
            onnx_path = model.export(
                format="onnx",
                opset=12,
                simplify=True,
            )
            print(f"  ✓ ONNX: {onnx_path}")
        except Exception as e:
            print(f"  ✗ Loi export ONNX: {e}")


def main() -> None:
    project_root = Path(__file__).resolve().parent
    cfg_path = project_root / "config_final.yaml"
    cfg = load_config(cfg_path)

    train_cfg = cfg["training"]
    data_yaml_path = project_root / "datasets" / "leaf_data_balanced" / "data_runtime_final.yaml"
    write_runtime_data_yaml(cfg, data_yaml_path)

    print_cuda_warning_if_needed()
    device = resolve_device(train_cfg.get("device", 0))
    print_header(cfg_path, device, data_yaml_path)
    project_abs, run_name = sanitize_output_settings(project_root, train_cfg)
    print(f"Output project  : {project_abs}")
    print(f"Output run name : {run_name}")
    assert_writable_output(project_abs, run_name)

    model = YOLO(train_cfg.get("model", "yolov8n.pt"))

    try:
        results = model.train(
            data=str(data_yaml_path),
            epochs=train_cfg.get("epochs", 100),
            imgsz=train_cfg.get("imgsz", 640),
            batch=train_cfg.get("batch", 16),
            device=device,
            project=project_abs,
            name=run_name,
            workers=train_cfg.get("workers", 8),
            patience=train_cfg.get("patience", 30),
            amp=train_cfg.get("amp", True),
            cos_lr=train_cfg.get("cos_lr", True),
            val=True,
            plots=True,
        )

        save_dir = Path(getattr(results, "save_dir", project_root / "runs" / "detect" / "train_final"))
        best_model_path = save_dir / "weights" / "best.pt"

        print("\n" + "=" * 70)
        print("HUAN LUYEN HOAN TAT")
        print("=" * 70)
        print(f"Thu muc ket qua : {save_dir.resolve()}")
        print(f"Best model (.pt): {best_model_path.resolve()}")

        if best_model_path.exists():
            export_models(best_model_path, cfg.get("export", {}))
        else:
            print("✗ Khong tim thay best.pt de export.")

        print("=" * 70 + "\n")

    except RuntimeError as e:
        print("\n" + "=" * 70)
        print("LOI RUNTIME KHI HUAN LUYEN")
        print("=" * 70)
        print(str(e))
        if "CUDA out of memory" in str(e):
            print("\nGoi y khac phuc CUDA OOM:")
            print("- Giam batch trong config_final.yaml (vd: 16 -> 8 hoac 4)")
            print("- Giam imgsz (vd: 640 -> 512)")
            print("- Dong ung dung khac dang dung GPU")
        print("=" * 70)
    except Exception as e:
        print("\n" + "=" * 70)
        print("LOI KHONG XAC DINH")
        print("=" * 70)
        print(str(e))
        traceback.print_exc()
        print("\nGoi y:")
        if isinstance(e, PermissionError):
            print("- Loi quyen ghi file. Thu dong file results.csv neu dang mo (Excel/Notepad).")
            print("- Khong de 'name' chua duong dan day du (chi de ten run, vi du: train_final15).")
            print("- Thu chay PowerShell voi quyen Run as Administrator.")
        print("- Kiem tra duong dan dataset trong config_final.yaml")
        print("- Chay: pip install ultralytics")
        print("- Kiem tra quyen ghi vao thu muc runs/")
        print("=" * 70)


if __name__ == "__main__":
    main()
