#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Phát hiện tự động:
  • CSV (Coffee clean — train_classes / test_classes + coffee-leaf-diseases/images)
  • CSV JSON RoCoLE (Coffee field — Annotations/RoCoLE-csv.csv + Photos/)
  • Thư mục theo tên lớp (Durian — train|val|test/<FOLDER>/)
  • garbage/ — ảnh nền, nhãn .txt rỗng
"""

from __future__ import annotations

import argparse
import csv
import json
import random
import shutil
import sys

# Windows console: tránh lỗi Unicode khi in tiếng Việt
def _ensure_utf8_stdio() -> None:
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            try:
                stream.reconfigure(encoding="utf-8", errors="replace")
            except Exception:
                pass


_ensure_utf8_stdio()

from collections import Counter
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Any, Dict, List, Optional, Sequence, Tuple

import yaml

try:
    from PIL import Image, ImageDraw, ImageFont

    _HAS_PIL = True
except ImportError:
    _HAS_PIL = False

try:
    from sklearn.model_selection import train_test_split

    _HAS_SK = True
except ImportError:
    _HAS_SK = False

# -----------------------------------------------------------------------------
# Terminal colors (ANSI)
# -----------------------------------------------------------------------------


class T:
    RESET = "\033[0m"
    BOLD = "\033[1m"
    RED = "\033[91m"
    GREEN = "\033[92m"
    YELLOW = "\033[93m"
    BLUE = "\033[94m"
    CYAN = "\033[96m"
    GRAY = "\033[90m"


def _supports_color() -> bool:
    if sys.platform == "win32":
        try:
            import ctypes

            kernel32 = ctypes.windll.kernel32
            kernel32.SetConsoleMode(kernel32.GetStdHandle(-11), 7)
        except Exception:
            pass
    return sys.stdout.isatty()


COLOR_ON = _supports_color()


def log(msg: str, level: str = "info") -> None:
    if not COLOR_ON:
        print(f"[{level.upper()}] {msg}")
        return
    if level == "ok":
        print(f"{T.GREEN}✔{T.RESET} {msg}")
    elif level == "warn":
        print(f"{T.YELLOW}⚠{T.RESET} {msg}")
    elif level == "err":
        print(f"{T.RED}✖{T.RESET} {msg}")
    elif level == "info":
        print(f"{T.CYAN}●{T.RESET} {msg}")
    elif level == "title":
        print(f"{T.BOLD}{T.BLUE}{msg}{T.RESET}")
    else:
        print(msg)


# -----------------------------------------------------------------------------
# Data structures
# -----------------------------------------------------------------------------


@dataclass
class Sample:
    """One training image + YOLO label lines (normalized 0–1)."""

    src_image: Path
    yolo_lines: List[str]
    strat_label: Any  # int class id or 'bg' for empty label
    source: str
    meta: str = ""


@dataclass
class Stats:
    by_source: Counter = field(default_factory=Counter)
    by_class: Counter = field(default_factory=Counter)
    skipped: Counter = field(default_factory=Counter)
    errors: List[str] = field(default_factory=list)


# -----------------------------------------------------------------------------
# Config
# -----------------------------------------------------------------------------


def load_config(path: Path) -> Dict[str, Any]:
    with open(path, "r", encoding="utf-8") as f:
        cfg = yaml.safe_load(f)
    return cfg


def build_durian_lookup(raw_map: Dict[str, Any]) -> Dict[str, int]:
    """Normalize keys: UPPER, lower, replace - with _"""
    out: Dict[str, int] = {}
    for k, v in (raw_map or {}).items():
        ks = [k, k.upper(), k.lower(), k.replace("-", "_").upper()]
        for kk in ks:
            out[kk] = int(v)
    return out


def default_bbox_line(class_id: int, cfg: Dict[str, Any]) -> str:
    d = cfg["default_bbox"]
    return f"{class_id} {d['x_center']} {d['y_center']} {d['width']} {d['height']}"


# -----------------------------------------------------------------------------
# Image validation
# -----------------------------------------------------------------------------

IMG_EXT = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}


def is_image(p: Path) -> bool:
    return p.suffix.lower() in IMG_EXT


def validate_image(path: Path, min_bytes: int) -> bool:
    try:
        if path.stat().st_size < min_bytes:
            return False
        if _HAS_PIL:
            with Image.open(path) as im:
                im.verify()
        return True
    except Exception:
        return False


def image_size(path: Path) -> Tuple[int, int]:
    with Image.open(path) as im:
        im = im.convert("RGB")
        return im.size  # w, h


# -----------------------------------------------------------------------------
# Coffee CSV (clean)
# -----------------------------------------------------------------------------


def row_to_class_id(row: Dict[str, str], cfg: Dict[str, Any]) -> Optional[int]:
    """Map miner/rust/phoma columns → class id."""
    cc = cfg["coffee_csv"]
    priority = cc["priority"]
    healthy = int(cc["healthy_class_id"])
    for rule in priority:
        col = rule["column"]
        val = row.get(col, "0").strip()
        if val == "1":
            return int(rule["class_id"])
    # all zero → healthy
    try:
        if all(int(row.get(r["column"], "0")) == 0 for r in priority):
            return healthy
    except ValueError:
        return None
    return healthy


def ingest_coffee_clean_csv(
    raw_clean: Path, cfg: Dict[str, Any], stats: Stats
) -> List[Sample]:
    out: List[Sample] = []
    coffee_root = raw_clean / "Coffee"
    cc = cfg["coffee_csv"]
    sub = cc["images_subdir"]
    train_csv = coffee_root / cc["train_file"]
    test_csv = coffee_root / cc["test_file"]
    dbox = lambda cid: default_bbox_line(cid, cfg)

    def process_csv(csv_path: Path, split_name: str) -> None:
        if not csv_path.exists():
            stats.skipped[f"missing_{csv_path.name}"] += 1
            return
        with open(csv_path, newline="", encoding="utf-8") as f:
            reader = csv.DictReader(f)
            for row in reader:
                rid = str(row.get("id", "")).strip()
                if not rid:
                    stats.skipped["csv_no_id"] += 1
                    continue
                img = coffee_root / sub / split_name / "images" / f"{rid}.jpg"
                if not img.exists():
                    for ext in (".jpg", ".jpeg", ".png"):
                        alt = coffee_root / sub / split_name / "images" / f"{rid}{ext}"
                        if alt.exists():
                            img = alt
                            break
                if not img.exists():
                    stats.skipped["csv_image_missing"] += 1
                    continue
                cid = row_to_class_id(row, cfg)
                if cid is None:
                    stats.skipped["csv_bad_row"] += 1
                    continue
                out.append(
                    Sample(
                        src_image=img,
                        yolo_lines=[dbox(cid)],
                        strat_label=cid,
                        source=f"clean_coffee_csv_{split_name}",
                        meta=f"id={rid}",
                    )
                )
                stats.by_class[cid] += 1

    process_csv(train_csv, "train")
    process_csv(test_csv, "test")
    stats.by_source["clean_coffee_csv"] = len(out)
    return out


# -----------------------------------------------------------------------------
# RoCoLE field (JSON in Label column)
# -----------------------------------------------------------------------------


def parse_labelbox_json(label_raw: str) -> Optional[Dict[str, Any]]:
    try:
        s = label_raw.replace('""', '"')
        return json.loads(s)
    except Exception:
        return None


def polygon_to_yolo_line(
    cls_id: int, geometry: List[Dict[str, Any]], w: int, h: int
) -> str:
    xs = [float(p["x"]) for p in geometry]
    ys = [float(p["y"]) for p in geometry]
    xmin, xmax = min(xs), max(xs)
    ymin, ymax = min(ys), max(ys)
    bw, bh = xmax - xmin, ymax - ymin
    if bw <= 1 or bh <= 1:
        bw = max(bw, 2.0)
        bh = max(bh, 2.0)
    xc = (xmin + xmax) / 2.0 / w
    yc = (ymin + ymax) / 2.0 / h
    nw = bw / w
    nh = bh / h
    xc = min(1.0, max(0.0, xc))
    yc = min(1.0, max(0.0, yc))
    nw = min(1.0, max(0.01, nw))
    nh = min(1.0, max(0.01, nh))
    return f"{cls_id} {xc:.6f} {yc:.6f} {nw:.6f} {nh:.6f}"


def map_roco_classification(name: str, cmap: Dict[str, int]) -> Optional[int]:
    if name in cmap:
        return int(cmap[name])
    # fuzzy
    n = name.strip().lower()
    for k, v in cmap.items():
        if k.lower() == n:
            return int(v)
    return None


def ingest_field_roco(raw_field: Path, cfg: Dict[str, Any], stats: Stats) -> List[Sample]:
    out: List[Sample] = []
    coffee = raw_field / "Coffee"
    ann = coffee / cfg["field_roco"]["annotations_subdir"]
    photos = coffee / cfg["field_roco"]["photos_subdir"]
    glob_pat = cfg["field_roco"]["csv_glob"]
    cmap = cfg["field_roco_classification"]

    csv_files = list(ann.glob(glob_pat))
    if not csv_files:
        log(f"Không tìm thấy {glob_pat} trong {ann}", "warn")
        return out

    csv_path = csv_files[0]
    with open(csv_path, newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            ext_id = (row.get("External ID") or "").strip()
            label_raw = row.get("Label") or ""
            if not ext_id or not label_raw:
                stats.skipped["roco_empty_row"] += 1
                continue
            img_path = photos / ext_id
            if not img_path.exists():
                # case-insensitive match
                low = ext_id.lower()
                found = None
                for p in photos.iterdir():
                    if p.name.lower() == low:
                        found = p
                        break
                img_path = found or img_path
            if not img_path.exists():
                stats.skipped["roco_image_missing"] += 1
                continue

            data = parse_labelbox_json(label_raw)
            if not data:
                stats.skipped["roco_bad_json"] += 1
                continue

            cls_name = data.get("classification")
            if not cls_name:
                stats.skipped["roco_no_classification"] += 1
                continue

            cls_id = map_roco_classification(str(cls_name), cmap)
            if cls_id is None:
                stats.skipped[f"roco_unknown:{cls_name}"] += 1
                continue

            try:
                w, h = image_size(img_path)
            except Exception:
                stats.skipped["roco_bad_image"] += 1
                continue

            leaves = data.get("Leaf") or []
            lines: List[str] = []
            if isinstance(leaves, list) and leaves:
                for leaf in leaves:
                    geom = leaf.get("geometry")
                    if geom and isinstance(geom, list):
                        lines.append(polygon_to_yolo_line(cls_id, geom, w, h))
            if not lines:
                lines = [default_bbox_line(cls_id, cfg)]

            out.append(
                Sample(
                    src_image=img_path,
                    yolo_lines=lines,
                    strat_label=cls_id,
                    source="field_roco_csv",
                    meta=ext_id,
                )
            )
            stats.by_class[cls_id] += 1

    stats.by_source["field_roco"] = len(out)
    return out


# -----------------------------------------------------------------------------
# Durian — folder names
# -----------------------------------------------------------------------------


def ingest_durian(
    raw_root: Path, cfg: Dict[str, Any], stats: Stats, tag: str
) -> List[Sample]:
    out: List[Sample] = []
    durian = raw_root / "Durian"
    if not durian.exists():
        return out

    fold_map = build_durian_lookup(cfg.get("durian_folder_map", {}))
    dbox = lambda cid: default_bbox_line(cid, cfg)

    for split in ("train", "val", "test"):
        sp = durian / split
        if not sp.is_dir():
            continue
        for class_dir in sp.iterdir():
            if not class_dir.is_dir():
                continue
            key = class_dir.name.upper().replace("-", "_")
            cid = fold_map.get(key) or fold_map.get(class_dir.name)
            if cid is None:
                stats.skipped[f"durian_unknown_folder:{class_dir.name}"] += 1
                continue
            for img_path in class_dir.rglob("*"):
                if not is_image(img_path):
                    continue
                out.append(
                    Sample(
                        src_image=img_path,
                        yolo_lines=[dbox(cid)],
                        strat_label=cid,
                        source=f"{tag}_durian_{split}",
                        meta=class_dir.name,
                    )
                )
                stats.by_class[cid] += 1

    stats.by_source[f"{tag}_durian"] = len(out)
    return out


# -----------------------------------------------------------------------------
# Garbage — empty labels
# -----------------------------------------------------------------------------


def ingest_garbage(raw_root: Path, stats: Stats, tag: str) -> List[Sample]:
    out: List[Sample] = []
    g = raw_root / "garbage"
    if not g.exists():
        return out
    for img_path in g.rglob("*"):
        if not is_image(img_path):
            continue
        out.append(
            Sample(
                src_image=img_path,
                yolo_lines=[],
                strat_label="bg",
                source=f"{tag}_garbage",
                meta="background",
            )
        )
    stats.by_source[f"{tag}_garbage"] = len(out)
    return out


# -----------------------------------------------------------------------------
# YOLO paired labels (optional)
# -----------------------------------------------------------------------------


def ingest_yolo_pairs(raw_root: Path, stats: Stats, tag: str, class_names: List[str]) -> List[Sample]:
    """Tìm cặp images/ + labels/ với file .txt tương ứng (class id giữ nguyên nếu trong 0–5)."""
    out: List[Sample] = []

    for images_dir in raw_root.rglob("images"):
        parent = images_dir.parent
        labels_dir = parent / "labels"
        if not labels_dir.is_dir():
            continue
        for img_path in images_dir.iterdir():
            if not is_image(img_path):
                continue
            lbl = labels_dir / (img_path.stem + ".txt")
            if not lbl.exists():
                continue
            lines_out: List[str] = []
            try:
                raw = lbl.read_text(encoding="utf-8").strip().splitlines()
                for ln in raw:
                    parts = ln.split()
                    if len(parts) < 5:
                        continue
                    cid = int(float(parts[0]))
                    if 0 <= cid < len(class_names):
                        lines_out.append(
                            " ".join([str(cid)] + parts[1:5])
                        )
            except Exception:
                stats.skipped["yolo_pair_bad"] += 1
                continue
            if not lines_out:
                continue
            primary = int(lines_out[0].split()[0])
            out.append(
                Sample(
                    src_image=img_path,
                    yolo_lines=lines_out,
                    strat_label=primary,
                    source=f"{tag}_yolo_pair",
                    meta=str(lbl),
                )
            )
            stats.by_class[primary] += 1

    if out:
        stats.by_source[f"{tag}_yolo_pairs"] = len(out)
    return out


# -----------------------------------------------------------------------------
# Merge, split, write
# -----------------------------------------------------------------------------


def filter_valid_samples(
    samples: Sequence[Sample], min_bytes: int, stats: Stats
) -> List[Sample]:
    ok: List[Sample] = []
    for s in samples:
        if not validate_image(s.src_image, min_bytes):
            stats.skipped["invalid_or_tiny_image"] += 1
            continue
        ok.append(s)
    return ok


def split_train_val(
    samples: Sequence[Sample], train_ratio: float, seed: int
) -> Tuple[List[Sample], List[Sample]]:
    labels = [s.strat_label for s in samples]
    counts = Counter(labels)
    use_strat = _HAS_SK and len(samples) >= 4 and all(v >= 2 for v in counts.values())
    if _HAS_SK and use_strat:
        try:
            train_s, val_s = train_test_split(
                list(samples),
                test_size=1.0 - train_ratio,
                random_state=seed,
                stratify=labels,
            )
            return train_s, val_s
        except Exception:
            pass
    # fallback: shuffle
    rng = random.Random(seed)
    items = list(samples)
    rng.shuffle(items)
    n_train = int(len(items) * train_ratio)
    return items[:n_train], items[n_train:]


def write_dataset(
    train: List[Sample],
    val: List[Sample],
    out_root: Path,
    cfg: Dict[str, Any],
    stats: Stats,
) -> None:
    prefixes = cfg["filename_prefix"]
    counter = {"c": 0, "f": 0}

    def pick_prefix(sample: Sample) -> str:
        if "clean" in sample.source or sample.source.startswith("clean"):
            return prefixes["clean"]
        if "field" in sample.source or sample.source.startswith("field"):
            return prefixes["field"]
        return prefixes["clean"]

    def dump(split_name: str, items: Sequence[Sample]) -> None:
        img_dir = out_root / split_name / "images"
        lbl_dir = out_root / split_name / "labels"
        img_dir.mkdir(parents=True, exist_ok=True)
        lbl_dir.mkdir(parents=True, exist_ok=True)

        used_names: set = set()

        for s in items:
            pfx = pick_prefix(s)
            key = "c" if pfx.startswith("c") else "f"
            counter[key] += 1
            stem = f"{pfx}{counter[key]:07d}_{s.src_image.stem}"
            dst_name = stem + s.src_image.suffix.lower()
            # collision guard
            while dst_name in used_names:
                counter[key] += 1
                stem = f"{pfx}{counter[key]:07d}_{s.src_image.stem}"
                dst_name = stem + s.src_image.suffix.lower()
            used_names.add(dst_name)

            dst_img = img_dir / dst_name
            try:
                shutil.copy2(s.src_image, dst_img)
            except Exception as e:
                stats.errors.append(f"copy {s.src_image} → {e}")
                continue

            lbl_path = lbl_dir / (stem + ".txt")
            lbl_path.write_text(
                "\n".join(s.yolo_lines) + ("\n" if s.yolo_lines else ""),
                encoding="utf-8",
            )

    if cfg["processing"].get("overwrite_output", True) and out_root.exists():
        shutil.rmtree(out_root)
    out_root.mkdir(parents=True, exist_ok=True)

    dump("train", train)
    dump("val", val)


def write_data_yaml(out_root: Path, class_names: List[str]) -> None:
    # Ultralytics-compatible
    content = {
        "path": str(out_root.resolve()),
        "train": "train/images",
        "val": "val/images",
        "nc": len(class_names),
        "names": class_names,
    }
    with open(out_root / "data.yaml", "w", encoding="utf-8") as f:
        yaml.dump(content, f, allow_unicode=True, default_flow_style=False, sort_keys=False)


def write_summary(
    out_root: Path,
    cfg: Dict[str, Any],
    stats: Stats,
    n_train: int,
    n_val: int,
) -> None:
    lines = []
    lines.append("Mat Than Nong — Dataset normalization summary")
    lines.append(f"Generated: {datetime.now().isoformat()}")
    lines.append("")
    lines.append(f"Output: {out_root.resolve()}")
    lines.append(f"Train samples: {n_train}")
    lines.append(f"Val samples:   {n_val}")
    lines.append("")
    lines.append("Per source:")
    for k, v in sorted(stats.by_source.items()):
        lines.append(f"  {k}: {v}")
    lines.append("")
    lines.append("Per class (labeled boxes; bg not counted):")
    names = cfg["class_names"]
    for i, n in enumerate(names):
        lines.append(f"  {i} {n}: {stats.by_class.get(i, 0)}")
    lines.append("")
    lines.append("Skipped / warnings:")
    for k, v in sorted(stats.skipped.items()):
        lines.append(f"  {k}: {v}")
    if stats.errors:
        lines.append("")
        lines.append("Errors:")
        for e in stats.errors[:50]:
            lines.append(f"  {e}")
    text = "\n".join(lines) + "\n"
    (out_root / "summary_report.txt").write_text(text, encoding="utf-8")


def visualize_samples(
    out_root: Path,
    cfg: Dict[str, Any],
    n_samples: int,
    seed: int,
) -> None:
    if not _HAS_PIL:
        log("Pillow không có — bỏ qua visualization.", "warn")
        return

    ver_dir = Path(cfg["paths"].get("verification_dir", "runs/data_verification"))
    ver_dir.mkdir(parents=True, exist_ok=True)
    names = cfg["class_names"]
    rng = random.Random(seed)

    train_img = out_root / "train" / "images"
    train_lbl = out_root / "train" / "labels"
    if not train_img.exists():
        return
    imgs = [p for p in train_img.iterdir() if is_image(p)]
    if not imgs:
        return
    picks = rng.sample(imgs, min(n_samples, len(imgs)))

    for i, ip in enumerate(picks):
        lp = train_lbl / (ip.stem + ".txt")
        lines: List[str] = []
        if lp.exists():
            lines = lp.read_text(encoding="utf-8").strip().splitlines()
        im = Image.open(ip).convert("RGB")
        w, h = im.size
        dr = ImageDraw.Draw(im)
        for ln in lines:
            parts = ln.split()
            if len(parts) < 5:
                continue
            cid = int(float(parts[0]))
            xc, yc, bw, bh = map(float, parts[1:5])
            x1 = (xc - bw / 2) * w
            y1 = (yc - bh / 2) * h
            x2 = (xc + bw / 2) * w
            y2 = (yc + bh / 2) * h
            dr.rectangle([x1, y1, x2, y2], outline="lime", width=max(2, min(w, h) // 400))
            label = names[cid] if 0 <= cid < len(names) else str(cid)
            dr.text((x1 + 2, y1 + 2), label, fill="yellow")
        im.save(ver_dir / f"verify_{i:02d}.png", format="PNG")

    log(f"Đã lưu {len(picks)} ảnh kiểm tra vào {ver_dir}", "ok")


# -----------------------------------------------------------------------------
# Main
# -----------------------------------------------------------------------------


def main() -> None:
    ap = argparse.ArgumentParser(description="Mat Than Nong unified YOLOv8 dataset builder")
    ap.add_argument("--config", default="config_unified.yaml", help="Path to YAML config")
    ap.add_argument("--dry-run", action="store_true", help="Only scan & report, no writes")
    ap.add_argument(
        "--only-visualize",
        action="store_true",
        help="Chỉ vẽ lại ảnh vào runs/data_verification (cần đã có datasets/leaf_data)",
    )
    args = ap.parse_args()

    cfg_path = Path(args.config)
    if not cfg_path.exists():
        log(f"Không tìm thấy config: {cfg_path}", "err")
        sys.exit(1)

    cfg = load_config(cfg_path)
    class_names: List[str] = cfg["class_names"]
    if len(class_names) != 6:
        log("class_names phải đúng 6 lớp.", "err")
        sys.exit(1)

    raw_clean = Path(cfg["paths"]["raw_clean"])
    raw_field = Path(cfg["paths"]["raw_field"])
    out_root = Path(cfg["paths"]["output"])
    vis_n = int(cfg["processing"].get("visualization_samples", 10))

    if args.only_visualize:
        if not (out_root / "train" / "images").exists():
            log(f"Không thấy {out_root}/train/images — chạy pipeline đầy đủ trước.", "err")
            sys.exit(1)
        visualize_samples(out_root, cfg, vis_n, int(cfg["split"]["seed"]))
        log("Chỉ tạo visualization — xong.", "ok")
        sys.exit(0)
    min_bytes = int(cfg["processing"]["min_file_size_bytes"])
    seed = int(cfg["split"]["seed"])
    train_ratio = float(cfg["split"]["train"])
    log("=== Mat Than Nong — Unified data pipeline ===", "title")
    log(f"RAW_CLEAN : {raw_clean}", "info")
    log(f"RAW_FIELD : {raw_field}", "info")
    log(f"OUTPUT    : {out_root}", "info")

    stats = Stats()
    all_samples: List[Sample] = []
    ingest = cfg.get("ingest") or {}
    use_clean = bool(ingest.get("use_raw_clean", True))
    use_field = bool(ingest.get("use_raw_field", True))

    # 1) Coffee CSV (clean)
    if use_clean and raw_clean.exists():
        all_samples.extend(ingest_coffee_clean_csv(raw_clean, cfg, stats))
        all_samples.extend(ingest_durian(raw_clean, cfg, stats, "clean"))
        all_samples.extend(ingest_garbage(raw_clean, stats, "clean"))
        all_samples.extend(ingest_yolo_pairs(raw_clean, stats, "clean", class_names))
    elif use_clean:
        log(f"Thiếu {raw_clean}", "warn")

    # 2) Field RoCoLE + Durian + garbage
    if use_field and raw_field.exists():
        all_samples.extend(ingest_field_roco(raw_field, cfg, stats))
        all_samples.extend(ingest_durian(raw_field, cfg, stats, "field"))
        all_samples.extend(ingest_garbage(raw_field, stats, "field"))
        all_samples.extend(ingest_yolo_pairs(raw_field, stats, "field", class_names))
    elif use_field:
        log(f"Thiếu {raw_field}", "warn")

    all_samples = filter_valid_samples(all_samples, min_bytes, stats)
    log(f"Tổng mẫu hợp lệ: {len(all_samples)}", "ok")

    if not all_samples:
        log("Không có mẫu nào — kiểm tra RAW_DATA_* và config.", "err")
        sys.exit(2)

    train_s, val_s = split_train_val(all_samples, train_ratio, seed)
    log(f"Chia train/val: {len(train_s)} / {len(val_s)} (train_ratio={train_ratio})", "ok")

    if args.dry_run:
        log("[DRY-RUN] Không ghi file. Thống kê:", "warn")
        log(f"  Train: {len(train_s)}  Val: {len(val_s)}", "info")
        for k, v in stats.by_source.items():
            log(f"  source {k}: {v}", "info")
        sys.exit(0)

    write_dataset(train_s, val_s, out_root, cfg, stats)
    write_data_yaml(out_root, class_names)
    write_summary(out_root, cfg, stats, len(train_s), len(val_s))
    visualize_samples(out_root, cfg, vis_n, seed)

    log(f"Hoàn tất. data.yaml: {out_root / 'data.yaml'}", "ok")
    log(f"Báo cáo: {out_root / 'summary_report.txt'}", "ok")


if __name__ == "__main__":
    main()
