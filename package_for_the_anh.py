"""
Dong goi du an train thanh file ZIP de chuyen cho The Anh.

Mac dinh script se dong goi:
- train_final.py
- config_final.yaml
- HD_TRIEN_KHAI.md
- requirements.txt
- datasets/leaf_data_balanced/**

Chay:
  python package_for_the_anh.py
  python package_for_the_anh.py --dry-run
  python package_for_the_anh.py --output "C:/Coffee_Durian_AI/exports/the_anh_training_bundle.zip"
"""

from __future__ import annotations

import argparse
import sys
from datetime import datetime
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile


def ensure_utf8_stdio() -> None:
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            try:
                stream.reconfigure(encoding="utf-8", errors="replace")
            except Exception:
                pass


def iter_files(base: Path, rel_path: Path):
    target = base / rel_path
    if target.is_file():
        yield target
        return
    if target.is_dir():
        for p in sorted(target.rglob("*")):
            if p.is_file():
                yield p


def format_size(num_bytes: int) -> str:
    units = ["B", "KB", "MB", "GB", "TB"]
    value = float(num_bytes)
    for u in units:
        if value < 1024.0 or u == units[-1]:
            return f"{value:.2f} {u}"
        value /= 1024.0
    return f"{num_bytes} B"


def collect_files(project_root: Path, include_paths: list[Path]) -> list[Path]:
    files: list[Path] = []
    seen: set[Path] = set()
    for rel in include_paths:
        for f in iter_files(project_root, rel):
            rf = f.resolve()
            if rf in seen:
                continue
            seen.add(rf)
            files.append(f)
    return files


def main() -> None:
    ensure_utf8_stdio()

    project_root = Path(__file__).resolve().parent
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    default_zip = project_root / "exports" / f"the_anh_training_bundle_{timestamp}.zip"

    parser = argparse.ArgumentParser(description="Dong goi zip cho The Anh training")
    parser.add_argument(
        "--output",
        type=Path,
        default=default_zip,
        help="Duong dan file zip dau ra",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Chi liet ke file, khong tao zip",
    )
    args = parser.parse_args()

    include = [
        Path("train_final.py"),
        Path("config_final.yaml"),
        Path("HD_TRIEN_KHAI.md"),
        Path("requirements.txt"),
        Path("datasets/leaf_data_balanced"),
    ]

    files = collect_files(project_root, include)
    if not files:
        print("[ERR] Khong tim thay file nao de dong goi.")
        sys.exit(1)

    missing = [p for p in include if not (project_root / p).exists()]
    if missing:
        print("[WARN] Mot so duong dan khong ton tai:")
        for m in missing:
            print(f"  - {m}")

    total_bytes = sum(f.stat().st_size for f in files)
    print("=" * 70)
    print("DANH SACH DONG GOI CHO THE ANH")
    print("=" * 70)
    print(f"So file         : {len(files)}")
    print(f"Tong dung luong : {format_size(total_bytes)}")
    print(f"Output zip      : {args.output}")
    print("-" * 70)

    preview = 20
    for f in files[:preview]:
        rel = f.relative_to(project_root)
        print(f"  {rel}")
    if len(files) > preview:
        print(f"  ... ({len(files) - preview} file nua)")
    print("=" * 70)

    if args.dry_run:
        print("[OK] Dry-run xong, khong tao zip.")
        return

    args.output.parent.mkdir(parents=True, exist_ok=True)
    with ZipFile(args.output, "w", compression=ZIP_DEFLATED) as zf:
        for f in files:
            arc = f.relative_to(project_root).as_posix()
            zf.write(f, arcname=arc)

    zip_size = args.output.stat().st_size
    print(f"[OK] Da tao zip: {args.output}")
    print(f"[OK] Kich thuoc zip: {format_size(zip_size)}")


if __name__ == "__main__":
    main()
