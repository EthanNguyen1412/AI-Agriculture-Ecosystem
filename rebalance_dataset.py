#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Rebalance YOLOv8 dataset: datasets/leaf_data → datasets/leaf_data_balanced

- Garbage: giữ đúng round(15% * N_labeled_unique) ảnh nền (nhãn rỗng).
- Lớp 0,1,2,4: nếu tổng < 2000 → kéo lên 3000 (chia 80/20 train/val). Lớp 5 giữ nguyên số lượng.
- Lớp 3: tối đa 4000 (chia 80/20).
- Không leakage: oversample chỉ trong train hoặc trong val sau khi đã stratified split.
"""

from __future__ import annotations

import argparse
import random
import shutil
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional, Tuple

import yaml

try:
    from PIL import Image, ImageDraw

    _HAS_PIL = True
except ImportError:
    _HAS_PIL = False

try:
    from sklearn.model_selection import train_test_split

    _HAS_SK = True
except ImportError:
    _HAS_SK = False

from tqdm import tqdm

# -----------------------------------------------------------------------------
# Windows UTF-8
# -----------------------------------------------------------------------------


def _ensure_utf8_stdio() -> None:
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            try:
                stream.reconfigure(encoding="utf-8", errors="replace")
            except Exception:
                pass


_ensure_utf8_stdio()

# -----------------------------------------------------------------------------
# Constants
# -----------------------------------------------------------------------------

IMG_EXT = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}
# Theo spec: oversample tới 3000 chỉ cho lớp 0,1,2,4 (<2000). Lớp 5 giữ tự nhiên.
MINORITY_OVERSAMPLE_CLASSES = (0, 1, 2, 4)
MAJORITY_CLASS = 3
MINORITY_TRIGGER = 2000  # if count < this → expand to MINORITY_TARGET
MINORITY_TARGET = 3000
MAJORITY_CAP = 4000
TRAIN_FRAC = 0.8
GARBAGE_FRAC = 0.15


@dataclass
class LabeledSample:
    """Một ảnh có nhãn (đường dẫn unique)."""

    img: Path
    lbl: Path
    primary: int
    resolved: Path


@dataclass
class OutputRecord:
    """Một bản ghi ghi ra disk (có thể trùng file nguồn khi oversample)."""

    src_img: Path
    is_garbage: bool
    src_lbl: Optional[Path] = None


def log(msg: str, kind: str = "info") -> None:
    prefix = {"info": "[INFO]", "ok": "[OK]", "warn": "[WARN]", "err": "[ERR]"}.get(
        kind, "[*]"
    )
    print(f"{prefix} {msg}")


def is_image(p: Path) -> bool:
    return p.suffix.lower() in IMG_EXT


def read_label_text(lbl: Path) -> str:
    if not lbl.exists():
        return ""
    return lbl.read_text(encoding="utf-8", errors="replace").strip()


def primary_class_from_label(text: str) -> Optional[int]:
    """Lớp chính = mode của các class id trong file; không có box hợp lệ → None (coi như garbage)."""
    lines = [ln.strip() for ln in text.splitlines() if ln.strip()]
    if not lines:
        return None
    ids: List[int] = []
    for ln in lines:
        parts = ln.split()
        if len(parts) >= 5:
            try:
                ids.append(int(float(parts[0])))
            except ValueError:
                continue
    if not ids:
        return None
    return Counter(ids).most_common(1)[0][0]


def scan_leaf_data(in_root: Path) -> Tuple[List[LabeledSample], List[Path], Dict[str, int]]:
    """
    Quét train/ + val/.
    Returns: labeled_samples (unique by img), garbage_img_paths, stats_before
    """
    labeled_map: Dict[Path, LabeledSample] = {}
    garbage: List[Path] = []

    for split in ("train", "val"):
        img_dir = in_root / split / "images"
        lbl_dir = in_root / split / "labels"
        if not img_dir.is_dir():
            continue
        for img in sorted(img_dir.iterdir()):
            if not is_image(img):
                continue
            lbl = lbl_dir / (img.stem + ".txt")
            text = read_label_text(lbl)
            pc = primary_class_from_label(text)
            resolved = img.resolve()

            if pc is None:
                garbage.append(resolved)
                continue

            if resolved in labeled_map:
                continue
            labeled_map[resolved] = LabeledSample(
                img=img, lbl=lbl, primary=pc, resolved=resolved
            )

    labeled = list(labeled_map.values())
    # before stats: per-class count (by primary), garbage
    by_c = Counter(s.primary for s in labeled)
    stats_before: Dict[str, int] = {f"class_{i}": by_c.get(i, 0) for i in range(6)}
    stats_before["garbage"] = len(garbage)
    stats_before["labeled_total"] = len(labeled)
    return labeled, garbage, stats_before


def apply_class3_cap(samples: List[LabeledSample], rng: random.Random) -> List[LabeledSample]:
    by_c = defaultdict(list)
    for s in samples:
        by_c[s.primary].append(s)
    c3 = by_c[MAJORITY_CLASS]
    if len(c3) <= MAJORITY_CAP:
        return samples
    kept = set(rng.sample(range(len(c3)), MAJORITY_CAP))
    new_c3 = [c3[i] for i in kept]
    rest = [s for s in samples if s.primary != MAJORITY_CLASS]
    return rest + new_c3


def compute_targets(samples: List[LabeledSample]) -> Dict[int, int]:
    """Tổng mục tiêu T[c] sau khi đã cap class 3."""
    by_c = Counter(s.primary for s in samples)
    T: Dict[int, int] = {}
    for c in range(6):
        n = by_c.get(c, 0)
        if c == MAJORITY_CLASS:
            T[c] = min(MAJORITY_CAP, n)
        elif c in MINORITY_OVERSAMPLE_CLASSES:
            T[c] = MINORITY_TARGET if n < MINORITY_TRIGGER else n
        else:
            # Lớp 5 (và mọi lớp không nằm trong nhóm trên): không ép 3000
            T[c] = n
    return T


def split_train_val_labeled(
    samples: List[LabeledSample], rng: int
) -> Tuple[List[LabeledSample], List[LabeledSample]]:
    y = [s.primary for s in samples]
    counts = Counter(y)
    if _HAS_SK and len(samples) >= 2 and counts and min(counts.values()) >= 2:
        try:
            tr, va = train_test_split(
                samples,
                test_size=1.0 - TRAIN_FRAC,
                random_state=rng,
                stratify=y,
            )
            return list(tr), list(va)
        except Exception:
            pass
    # fallback shuffle
    rnd = random.Random(rng)
    items = list(samples)
    rnd.shuffle(items)
    n_tr = int(len(items) * TRAIN_FRAC)
    return items[:n_tr], items[n_tr:]


def split_targets(T_c: int) -> Tuple[int, int]:
    n_tr = int(T_c * TRAIN_FRAC)
    n_va = T_c - n_tr
    return n_tr, n_va


def balance_side(
    side: List[LabeledSample],
    targets: Dict[int, int],
    side_name: str,
    rng: random.Random,
) -> List[OutputRecord]:
    """Đạt đúng số mục tiêu từng lớp trên một phía (train hoặc val) bằng over/under sampling."""
    by_c: Dict[int, List[LabeledSample]] = defaultdict(list)
    for s in side:
        by_c[s.primary].append(s)

    out: List[OutputRecord] = []
    for c in range(6):
        pool = by_c.get(c, [])
        t_total = targets[c]
        t_tr, t_va = split_targets(t_total)
        want = t_tr if side_name == "train" else t_va
        if want <= 0:
            continue
        if len(pool) == 0:
            log(f"Lớp {c} không có mẫu trên {side_name} — bỏ qua lớp này.", "warn")
            continue
        if len(pool) > want:
            idx = rng.sample(range(len(pool)), want)
            chosen = [pool[i] for i in idx]
        else:
            chosen = list(pool)
            while len(chosen) < want:
                chosen.append(rng.choice(pool))
        for s in chosen:
            out.append(
                OutputRecord(src_img=s.img, is_garbage=False, src_lbl=s.lbl)
            )
    return out


def split_garbage(
    garbage: List[Path], n_keep: int, seed: int
) -> Tuple[List[Path], List[Path]]:
    rng = random.Random(seed)
    g = list(garbage)
    rng.shuffle(g)
    n_keep = min(n_keep, len(g))
    picked = g[:n_keep]
    n_tr = int(round(TRAIN_FRAC * n_keep))
    n_tr = max(0, min(n_tr, n_keep))
    tr = picked[:n_tr]
    va = picked[n_tr:]
    return tr, va


def write_outputs(
    train_recs: List[OutputRecord],
    val_recs: List[OutputRecord],
    out_root: Path,
) -> None:
    if out_root.exists():
        shutil.rmtree(out_root)
    for split, recs in (("train", train_recs), ("val", val_recs)):
        img_d = out_root / split / "images"
        lbl_d = out_root / split / "labels"
        img_d.mkdir(parents=True, exist_ok=True)
        lbl_d.mkdir(parents=True, exist_ok=True)
        for i, rec in enumerate(
            tqdm(recs, desc=f"Ghi {split}", unit="file", ncols=88)
        ):
            ext = rec.src_img.suffix.lower() or ".jpg"
            stem = f"b_{split[:2]}_{i:07d}_{rec.src_img.stem}"[:180]
            dst_i = img_d / f"{stem}{ext}"
            dst_l = lbl_d / f"{stem}.txt"
            shutil.copy2(rec.src_img, dst_i)
            if rec.is_garbage:
                dst_l.write_text("", encoding="utf-8")
            else:
                if rec.src_lbl and rec.src_lbl.exists():
                    shutil.copy2(rec.src_lbl, dst_l)
                else:
                    dst_l.write_text("", encoding="utf-8")


def write_data_yaml(out_root: Path, names: List[str]) -> None:
    data = {
        "path": str(out_root.resolve()),
        "train": "train/images",
        "val": "val/images",
        "nc": len(names),
        "names": names,
    }
    with open(out_root / "data.yaml", "w", encoding="utf-8") as f:
        yaml.dump(data, f, allow_unicode=True, default_flow_style=False, sort_keys=False)


def count_records_by_class(recs: List[OutputRecord]) -> Counter:
    ctr: Counter = Counter()
    for r in recs:
        if r.is_garbage:
            ctr["garbage"] += 1
            continue
        if r.src_lbl and r.src_lbl.exists():
            text = read_label_text(r.src_lbl)
            pc = primary_class_from_label(text)
            if pc is not None:
                ctr[f"class_{pc}"] += 1
    return ctr


def visualize(
    out_root: Path, names: List[str], n: int, seed: int
) -> None:
    if not _HAS_PIL:
        log("Không có Pillow — bỏ qua visualization.", "warn")
        return
    ver = Path("runs/data_verification_balanced")
    ver.mkdir(parents=True, exist_ok=True)
    rng = random.Random(seed)
    timg = out_root / "train" / "images"
    tlbl = out_root / "train" / "labels"
    imgs = [p for p in timg.iterdir() if is_image(p)]
    if not imgs:
        return
    picks = rng.sample(imgs, min(n, len(imgs)))
    for i, ip in enumerate(tqdm(picks, desc="Visualization", ncols=88)):
        lp = tlbl / (ip.stem + ".txt")
        lines = read_label_text(lp).splitlines() if lp.exists() else []
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
            dr.rectangle(
                [x1, y1, x2, y2],
                outline="lime",
                width=max(2, min(w, h) // 400),
            )
            lab = names[cid] if 0 <= cid < len(names) else str(cid)
            dr.text((x1 + 2, y1 + 2), lab, fill="yellow")
        im.save(ver / f"balanced_{i:02d}.png", format="PNG")
    log(f"Đã lưu visualization vào {ver}", "ok")


def write_report(
    path: Path,
    stats_before: Dict[str, int],
    after_train: Counter,
    after_val: Counter,
    meta: Dict[str, object],
) -> None:
    lines = [
        "YOLOv8 Dataset Rebalance Report",
        f"Generated: {datetime.now().isoformat()}",
        "",
        "=== BEFORE (datasets/leaf_data) ===",
    ]
    for k in sorted(stats_before.keys()):
        lines.append(f"  {k}: {stats_before[k]}")
    lines.extend(
        [
            "",
            "=== PARAMETERS ===",
            f"  garbage_frac_of_labeled: {meta.get('garbage_frac')}",
            f"  minority_trigger: {meta.get('minority_trigger')}",
            f"  minority_target: {meta.get('minority_target')}",
            f"  majority_cap: {meta.get('majority_cap')}",
            f"  train_frac: {meta.get('train_frac')}",
            f"  garbage_kept: {meta.get('garbage_kept')}",
            "",
            "=== AFTER — TRAIN ===",
        ]
    )
    for k in sorted(after_train.keys()):
        lines.append(f"  {k}: {after_train[k]}")
    lines.append(f"  TOTAL: {sum(after_train.values())}")
    lines.extend(["", "=== AFTER — VAL ==="])
    for k in sorted(after_val.keys()):
        lines.append(f"  {k}: {after_val[k]}")
    lines.append(f"  TOTAL: {sum(after_val.values())}")
    lines.append("")
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def plan_and_build(
    in_root: Path,
    out_root: Path,
    seed: int,
    dry_run: bool,
) -> None:
    rng = random.Random(seed)

    log(f"Đọc dữ liệu từ {in_root.resolve()}", "info")
    labeled, garbage, stats_before = scan_leaf_data(in_root)
    n_labeled = stats_before["labeled_total"]
    n_garbage_keep = int(round(GARBAGE_FRAC * n_labeled))
    n_garbage_keep = max(0, min(n_garbage_keep, len(garbage)))

    log(
        f"Labeled unique: {n_labeled}, garbage pool: {len(garbage)}, "
        f"giữ garbage: {n_garbage_keep} (~{GARBAGE_FRAC*100:.0f}% labeled)",
        "info",
    )

    # Cap class 3
    after_cap = apply_class3_cap(labeled, rng)
    targets = compute_targets(after_cap)
    log(f"Mục tiêu T[c] sau cap/oversample plan: {dict(targets)}", "info")

    tr_lbl, va_lbl = split_train_val_labeled(after_cap, seed)

    train_recs = balance_side(tr_lbl, targets, "train", rng)
    val_recs = balance_side(va_lbl, targets, "val", rng)

    g_tr, g_va = split_garbage(garbage, n_garbage_keep, seed)
    for p in g_tr:
        train_recs.append(OutputRecord(src_img=p, is_garbage=True))
    for p in g_va:
        val_recs.append(OutputRecord(src_img=p, is_garbage=True))

    after_tr = count_records_by_class(train_recs)
    after_va = count_records_by_class(val_recs)

    meta = {
        "garbage_frac": GARBAGE_FRAC,
        "minority_trigger": MINORITY_TRIGGER,
        "minority_target": MINORITY_TARGET,
        "majority_cap": MAJORITY_CAP,
        "train_frac": TRAIN_FRAC,
        "garbage_kept": n_garbage_keep,
    }

    if dry_run:
        log("=== DRY RUN — không ghi file ===", "warn")
        log(f"Train records: {len(train_recs)}, Val records: {len(val_recs)}", "info")
        write_report(
            Path("rebalance_report_dryrun.txt"),
            stats_before,
            after_tr,
            after_va,
            meta,
        )
        log("Đã ghi rebalance_report_dryrun.txt", "ok")
        return

    names = []
    src_yaml = in_root / "data.yaml"
    if src_yaml.exists():
        with open(src_yaml, encoding="utf-8") as f:
            d = yaml.safe_load(f)
        names = d.get("names") or []
    if len(names) != 6:
        names = [
            "cafe_gisat",
            "cafe_dommatcua",
            "cafe_khoe",
            "saurieng_chayla",
            "saurieng_domtao",
            "saurieng_khoe",
        ]

    # Fix garbage OutputRecord: src must be Path under in_root (resolved img path was used)
    # split_garbage used paths from scan — they are resolved; stem still works
    write_outputs(train_recs, val_recs, out_root)
    write_data_yaml(out_root, names)
    write_report(
        out_root / "rebalance_report.txt",
        stats_before,
        after_tr,
        after_va,
        meta,
    )
    visualize(out_root, names, 10, seed)
    log(f"Xong: {out_root}", "ok")


def main() -> None:
    ap = argparse.ArgumentParser(description="Rebalance leaf_data → leaf_data_balanced")
    ap.add_argument(
        "--input",
        type=Path,
        default=Path("datasets/leaf_data"),
        help="Thư mục dataset gốc",
    )
    ap.add_argument(
        "--output",
        type=Path,
        default=Path("datasets/leaf_data_balanced"),
        help="Thư mục dataset cân bằng",
    )
    ap.add_argument("--seed", type=int, default=42)
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    if not args.input.is_dir():
        log(f"Không thấy {args.input}", "err")
        sys.exit(1)

    plan_and_build(args.input, args.output, args.seed, args.dry_run)


if __name__ == "__main__":
    main()
