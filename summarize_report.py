from __future__ import annotations

import argparse
import csv
from collections import Counter
from pathlib import Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Summarize evaluation CSV files for reporting metrics."
    )
    parser.add_argument(
        "--eval-csv",
        type=Path,
        default=Path("reports") / "real_world_eval" / "image_eval.csv",
        help="Path to image_eval.csv",
    )
    parser.add_argument(
        "--out-csv",
        type=Path,
        default=Path("reports") / "real_world_eval" / "metrics_summary.csv",
        help="Path to output metrics summary CSV",
    )
    parser.add_argument(
        "--topk-confusions",
        type=int,
        default=10,
        help="Number of top confusion pairs to export.",
    )
    return parser.parse_args()


def read_eval_rows(path: Path) -> list[dict[str, str]]:
    if not path.exists():
        raise FileNotFoundError(f"Not found: {path}")
    with open(path, "r", encoding="utf-8", newline="") as f:
        return list(csv.DictReader(f))


def safe_div(num: float, den: float) -> float:
    return num / den if den else 0.0


def compute_metrics(rows: list[dict[str, str]]) -> tuple[list[list[object]], list[list[object]]]:
    known_rows = [r for r in rows if r["ground_truth_label"] != "__unknown__"]
    y_true = [r["ground_truth_label"] for r in known_rows]
    y_pred = [r["predicted_top_label"] for r in known_rows]

    labels = sorted(set(y_true) | set(y_pred))
    true_counter = Counter(y_true)
    pred_counter = Counter(y_pred)

    tp_counter: Counter[str] = Counter()
    confusion_pairs: Counter[tuple[str, str]] = Counter()

    correct = 0
    for t, p in zip(y_true, y_pred):
        if t == p:
            correct += 1
            tp_counter[t] += 1
        else:
            confusion_pairs[(t, p)] += 1

    accuracy = safe_div(correct, len(known_rows))
    macro_p = 0.0
    macro_r = 0.0
    macro_f1 = 0.0

    per_class_rows: list[list[object]] = []
    for label in labels:
        tp = tp_counter[label]
        fp = pred_counter[label] - tp
        fn = true_counter[label] - tp
        precision = safe_div(tp, tp + fp)
        recall = safe_div(tp, tp + fn)
        f1 = safe_div(2 * precision * recall, precision + recall)

        macro_p += precision
        macro_r += recall
        macro_f1 += f1

        per_class_rows.append(
            [
                label,
                true_counter[label],
                pred_counter[label],
                tp,
                round(precision, 6),
                round(recall, 6),
                round(f1, 6),
            ]
        )

    if labels:
        macro_p /= len(labels)
        macro_r /= len(labels)
        macro_f1 /= len(labels)

    summary_rows: list[list[object]] = [
        ["num_images_known_gt", len(known_rows)],
        ["accuracy_top1", round(accuracy, 6)],
        ["macro_precision", round(macro_p, 6)],
        ["macro_recall", round(macro_r, 6)],
        ["macro_f1", round(macro_f1, 6)],
    ]

    top_confusions = confusion_pairs.most_common()
    return summary_rows + [["", ""]] + [["class", "support", "pred_count", "tp", "precision", "recall", "f1"]] + per_class_rows, [
        [t, p, c] for (t, p), c in top_confusions
    ]


def write_csv(path: Path, header: list[str], rows: list[list[object]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(header)
        writer.writerows(rows)


def main() -> None:
    args = parse_args()
    rows = read_eval_rows(args.eval_csv.resolve())

    metrics_rows, confusions_rows = compute_metrics(rows)
    topk = max(args.topk_confusions, 0)
    confusions_rows = confusions_rows[:topk]

    out_csv = args.out_csv.resolve()
    write_csv(out_csv, ["metric_or_class", "value_1", "value_2", "value_3", "value_4", "value_5", "value_6"], metrics_rows)
    write_csv(
        out_csv.parent / "top_confusions.csv",
        ["true_label", "pred_label", "count"],
        confusions_rows,
    )

    print(f"Saved: {out_csv}")
    print(f"Saved: {out_csv.parent / 'top_confusions.csv'}")
    if confusions_rows:
        print("\nTop confusion pairs:")
        for t, p, c in confusions_rows[:5]:
            print(f"- {t} -> {p}: {c}")


if __name__ == "__main__":
    main()
