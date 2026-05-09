from __future__ import annotations

import argparse
import shutil
import sys
from pathlib import Path


def _eprint(msg: str) -> None:
    print(msg, file=sys.stderr)


def _import_ultralytics_yolo():
    try:
        from ultralytics import YOLO  # type: ignore

        return YOLO, None
    except Exception as e:  # noqa: BLE001
        return None, e


def _import_tensorflow():
    try:
        import tensorflow as tf  # type: ignore

        return tf, None
    except Exception as e:  # noqa: BLE001
        return None, e


def resolve_best_pt(run_dir: Path) -> Path:
    weights_dir = run_dir / "weights"
    best_pt = weights_dir / "best.pt"
    if best_pt.exists():
        return best_pt
    raise FileNotFoundError(f"Khong tim thay '{best_pt}'.")


def main() -> int:
    ap = argparse.ArgumentParser(
        description="Export YOLO (Ultralytics) best.pt -> TFLite",
    )
    ap.add_argument(
        "--run-dir",
        default="train_final17",
        help="Thu muc ket qua train (vi du: train_final17)",
    )
    ap.add_argument(
        "--out-dir",
        default="exports",
        help="Thu muc xuat file (mac dinh: exports/)",
    )
    ap.add_argument(
        "--name",
        default="best",
        help="Ten file output (mac dinh: best -> best.tflite)",
    )
    ap.add_argument(
        "--imgsz",
        type=int,
        default=640,
        help="Kich thuoc anh khi export (mac dinh: 640)",
    )
    ap.add_argument(
        "--int8",
        action="store_true",
        help="Bat INT8 quantization (can them dataset calibration trong mot so truong hop)",
    )

    args = ap.parse_args()
    project_root = Path(__file__).resolve().parent
    run_dir = (project_root / args.run_dir).resolve()
    out_dir = (project_root / args.out_dir).resolve()
    out_dir.mkdir(parents=True, exist_ok=True)

    best_pt = resolve_best_pt(run_dir)

    YOLO, yolo_err = _import_ultralytics_yolo()
    if YOLO is None:
        _eprint("✗ Chua co thu vien 'ultralytics' trong moi truong Python hien tai.")
        _eprint(f"  Loi import: {yolo_err}")
        _eprint("\nCach cai nhanh:")
        _eprint("  pip install -r requirements.txt")
        _eprint("  # hoac")
        _eprint("  pip install ultralytics")
        return 2

    tf, tf_err = _import_tensorflow()
    if tf is None:
        _eprint("✗ Chua co 'tensorflow' => khong the export .tflite trong moi truong hien tai.")
        _eprint(f"  Loi import: {tf_err}")
        _eprint("\nCach cai nhanh (CPU):")
        _eprint("  pip install tensorflow")
        _eprint("\nNeu pip khong co tensorflow cho Python ban dang dung, hay tao venv Python khac (vd 3.10/3.11) va cai lai.")
        return 3

    print("=" * 70)
    print("EXPORT TFLITE (Ultralytics)")
    print(f"Run dir : {run_dir}")
    print(f"Input   : {best_pt}")
    print(f"Out dir : {out_dir}")
    print(f"imgsz   : {args.imgsz}")
    print(f"int8    : {args.int8}")
    print("=" * 70)

    model = YOLO(str(best_pt))
    try:
        exported = model.export(
            format="tflite",
            imgsz=args.imgsz,
            int8=bool(args.int8),
            half=False,
        )
    except Exception as e:  # noqa: BLE001
        _eprint("\n✗ Export TFLite that bai.")
        _eprint(str(e))
        _eprint(
            "\nGoi y: neu loi lien quan 'onnx'/'onnxsim'/'flatbuffers' hay cai them cac goi phu thuoc theo thong bao."
        )
        return 4

    exported_path = Path(str(exported)).resolve()
    if not exported_path.exists():
        _eprint(f"✗ Khong tim thay file export duoc: {exported_path}")
        return 5

    final_path = out_dir / f"{args.name}.tflite"
    shutil.copy2(exported_path, final_path)
    print(f"\n✓ Da xuat: {final_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
