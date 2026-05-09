from __future__ import annotations

import argparse
import csv
import time
from collections import Counter, defaultdict
from pathlib import Path
from typing import Iterable

from ultralytics import YOLO


IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run best.pt inference on real-world test folders and export report CSV files."
    )
    parser.add_argument(
        "--model",
        type=Path,
        required=True,
        help="Path to trained YOLO weights (best.pt).",
    )
    parser.add_argument(
        "--input-dirs",
        type=Path,
        nargs="+",
        required=True,
        help="One or more folders containing real-world test images.",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path("reports") / "real_world_eval",
        help="Output folder for CSV reports and optional visualized predictions.",
    )
    parser.add_argument("--imgsz", type=int, default=640, help="Inference image size.")
    parser.add_argument("--conf", type=float, default=0.25, help="Confidence threshold.")
    parser.add_argument(
        "--exclude-dirs",
        nargs="*",
        default=["masks", "labels", "label"],
        help="Folder names to skip while collecting images (default: masks, labels, label).",
    )
    parser.add_argument(
        "--save-vis",
        action="store_true",
        help="Save visualized predictions into output directory.",
    )
    parser.add_argument(
        "--coffee-label-csv",
        type=Path,
        default=Path("RAW_DATA_CLEAN") / "Coffee" / "test_classes.csv",
        help="CSV with Coffee ground-truth labels (id + one-hot columns).",
    )
    parser.add_argument(
        "--class-map",
        nargs="*",
        default=[],
        help=(
            "Optional mapping pairs gt_label=model_label. "
            "Example: miner=cafe_gisat ALGAL_LEAF_SPOT=saurieng_domtao"
        ),
    )
    return parser.parse_args()


def collect_images(folders: Iterable[Path], exclude_dirs: set[str]) -> list[Path]:
    images: list[Path] = []
    for folder in folders:
        if not folder.exists():
            print(f"[WARN] Folder not found: {folder}")
            continue
        if not folder.is_dir():
            print(f"[WARN] Not a directory: {folder}")
            continue
        for path in folder.rglob("*"):
            if path.is_file() and path.suffix.lower() in IMAGE_EXTENSIONS:
                rel_parts = [part.lower() for part in path.relative_to(folder).parts[:-1]]
                if any(part in exclude_dirs for part in rel_parts):
                    continue
                images.append(path)
    return sorted(images)


def write_csv(path: Path, header: list[str], rows: list[list[object]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(header)
        writer.writerows(rows)


def normalize_label(s: str) -> str:
    return "".join(ch.lower() for ch in s if ch.isalnum())


def parse_class_map(pairs: list[str]) -> dict[str, str]:
    mapping: dict[str, str] = {}
    for pair in pairs:
        if "=" not in pair:
            continue
        left, right = pair.split("=", 1)
        left = left.strip()
        right = right.strip()
        if left and right:
            mapping[normalize_label(left)] = right
    return mapping


def read_coffee_gt(csv_path: Path, class_map: dict[str, str]) -> dict[str, str]:
    if not csv_path.exists():
        print(f"[WARN] Coffee label CSV not found: {csv_path}")
        return {}

    id_to_gt: dict[str, str] = {}
    with open(csv_path, "r", encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        class_cols = [c for c in (reader.fieldnames or []) if c != "id"]
        for row in reader:
            image_id = str(row.get("id", "")).strip()
            if not image_id:
                continue
            positive_cols = []
            for col in class_cols:
                value = str(row.get(col, "")).strip()
                if value == "1":
                    positive_cols.append(col)
            if not positive_cols:
                continue
            gt_raw = positive_cols[0]
            mapped = class_map.get(normalize_label(gt_raw), gt_raw)
            id_to_gt[image_id] = mapped
    return id_to_gt


def infer_gt_label(
    image_path: Path,
    coffee_gt: dict[str, str],
    class_map: dict[str, str],
) -> str:
    # Coffee folder has /test/images/{id}.jpg and labels live in test_classes.csv.
    image_stem = image_path.stem
    if image_stem in coffee_gt:
        return coffee_gt[image_stem]

    # Durian folder uses class-name subfolders.
    parent_label = image_path.parent.name
    if normalize_label(parent_label) == "images":
        return "__unknown__"
    return class_map.get(normalize_label(parent_label), parent_label)


def build_confusion_matrix(
    y_true: list[str],
    y_pred: list[str],
) -> tuple[list[str], list[list[int]]]:
    labels = sorted(set(y_true) | set(y_pred))
    index = {label: i for i, label in enumerate(labels)}
    matrix = [[0 for _ in labels] for _ in labels]
    for t, p in zip(y_true, y_pred):
        matrix[index[t]][index[p]] += 1
    return labels, matrix


def main() -> None:
    args = parse_args()
    model_path = args.model.resolve()
    if not model_path.exists():
        raise FileNotFoundError(f"Model not found: {model_path}")

    exclude_dirs = {d.lower().strip() for d in args.exclude_dirs if d.strip()}
    images = collect_images(args.input_dirs, exclude_dirs)
    if not images:
        raise FileNotFoundError("No images found in --input-dirs.")

    out_dir = args.output_dir.resolve()
    out_dir.mkdir(parents=True, exist_ok=True)

    model = YOLO(str(model_path))
    class_names = model.names
    class_map = parse_class_map(args.class_map)
    coffee_gt = read_coffee_gt(args.coffee_label_csv.resolve(), class_map)

    detection_rows: list[list[object]] = []
    image_rows: list[list[object]] = []
    class_counter: Counter[str] = Counter()
    folder_counter: Counter[str] = Counter()
    folder_conf_sum: defaultdict[str, float] = defaultdict(float)
    total_infer_ms = 0.0
    y_true: list[str] = []
    y_pred: list[str] = []

    print(f"Model       : {model_path}")
    print(f"Images found: {len(images)}")
    print(f"Output dir  : {out_dir}")

    for idx, image_path in enumerate(images, start=1):
        t0 = time.perf_counter()
        results = model.predict(
            source=str(image_path),
            imgsz=args.imgsz,
            conf=args.conf,
            verbose=False,
            save=args.save_vis,
            project=str(out_dir),
            name="visualizations",
            exist_ok=True,
        )
        infer_ms = (time.perf_counter() - t0) * 1000.0
        total_infer_ms += infer_ms

        result = results[0]
        folder_name = image_path.parent.name
        boxes = result.boxes

        top_class = "no_detection"
        top_conf = 0.0
        det_count = 0

        if boxes is not None and len(boxes) > 0:
            for box in boxes:
                cls_id = int(box.cls.item())
                conf = float(box.conf.item())
                xyxy = box.xyxy[0].tolist()
                cls_name = class_names.get(cls_id, str(cls_id))

                detection_rows.append(
                    [
                        str(image_path),
                        folder_name,
                        cls_id,
                        cls_name,
                        round(conf, 6),
                        round(xyxy[0], 2),
                        round(xyxy[1], 2),
                        round(xyxy[2], 2),
                        round(xyxy[3], 2),
                    ]
                )

                class_counter[cls_name] += 1
                folder_counter[folder_name] += 1
                folder_conf_sum[folder_name] += conf
                det_count += 1

                if conf > top_conf:
                    top_conf = conf
                    top_class = cls_name

        image_rows.append(
            [
                str(image_path),
                folder_name,
                det_count,
                top_class,
                round(top_conf, 6),
                round(infer_ms, 2),
            ]
        )

        gt_label = infer_gt_label(image_path, coffee_gt, class_map)
        y_true.append(gt_label)
        y_pred.append(top_class)

        if idx % 20 == 0 or idx == len(images):
            print(f"- Processed {idx}/{len(images)} images")

    class_rows = [[k, v] for k, v in class_counter.most_common()]
    folder_rows: list[list[object]] = []
    for folder_name, det_total in sorted(folder_counter.items()):
        avg_conf = folder_conf_sum[folder_name] / det_total if det_total else 0.0
        folder_rows.append([folder_name, det_total, round(avg_conf, 6)])

    known_pairs = [(t, p) for t, p in zip(y_true, y_pred) if t != "__unknown__"]
    known_true = [t for t, _ in known_pairs]
    known_pred = [p for _, p in known_pairs]

    avg_infer_ms = total_infer_ms / len(images)
    summary_rows = [
        ["num_images", len(images)],
        ["num_detections", len(detection_rows)],
        ["avg_infer_ms_per_image", round(avg_infer_ms, 4)],
        ["approx_fps", round(1000.0 / avg_infer_ms, 4) if avg_infer_ms > 0 else 0.0],
        ["num_images_with_gt", len(y_true)],
        ["num_images_with_known_gt", len(known_true)],
    ]

    labels, matrix = build_confusion_matrix(known_true, known_pred)
    confusion_rows: list[list[object]] = []
    header = ["true_label\\pred_label"] + labels
    for i, true_label in enumerate(labels):
        row: list[object] = [true_label]
        row.extend(matrix[i])
        confusion_rows.append(row)

    write_csv(
        out_dir / "detections.csv",
        ["image_path", "source_folder", "class_id", "class_name", "confidence", "x1", "y1", "x2", "y2"],
        detection_rows,
    )
    write_csv(
        out_dir / "image_summary.csv",
        ["image_path", "source_folder", "num_detections", "top_class", "top_confidence", "infer_ms"],
        image_rows,
    )
    write_csv(out_dir / "class_stats.csv", ["class_name", "num_detections"], class_rows)
    write_csv(out_dir / "folder_stats.csv", ["source_folder", "num_detections", "avg_confidence"], folder_rows)
    write_csv(out_dir / "run_summary.csv", ["metric", "value"], summary_rows)
    write_csv(
        out_dir / "image_eval.csv",
        ["image_path", "source_folder", "ground_truth_label", "predicted_top_label", "is_correct"],
        [
            [img[0], img[1], t, p, int(t == p)]
            for img, t, p in zip(image_rows, y_true, y_pred)
        ],
    )
    write_csv(out_dir / "confusion_matrix.csv", header, confusion_rows)

    print("\nDone. Exported files:")
    print(f"- {out_dir / 'detections.csv'}")
    print(f"- {out_dir / 'image_summary.csv'}")
    print(f"- {out_dir / 'class_stats.csv'}")
    print(f"- {out_dir / 'folder_stats.csv'}")
    print(f"- {out_dir / 'run_summary.csv'}")
    print(f"- {out_dir / 'image_eval.csv'}")
    print(f"- {out_dir / 'confusion_matrix.csv'}")


if __name__ == "__main__":
    main()
