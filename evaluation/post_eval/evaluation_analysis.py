import argparse
import json
from pathlib import Path
from statistics import median
from typing import Any, Dict, List, Optional


def to_float(value: Any) -> Optional[float]:
    if isinstance(value, bool):
        return None
    if isinstance(value, (int, float)):
        return float(value)
    return None


def read_json(path: Path) -> Dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def sorted_values(values: List[float]) -> List[float]:
    return sorted(values)


def percentile(values: List[float], p: float) -> Optional[float]:
    if not values:
        return None
    if len(values) == 1:
        return values[0]

    rank = (len(values) - 1) * p
    low_idx = int(rank)
    high_idx = min(low_idx + 1, len(values) - 1)
    frac = rank - low_idx
    low = values[low_idx]
    high = values[high_idx]
    return low + (high - low) * frac


def summarize(values: List[float]) -> Dict[str, Any]:
    if not values:
        return {
            "count": 0,
            "mean": None,
            "median": None,
            "min": None,
            "max": None,
            "p25": None,
            "p75": None,
        }

    data = sorted_values(values)
    count = len(data)
    mean = sum(data) / count

    return {
        "count": count,
        "mean": mean,
        "median": median(data),
        "min": data[0],
        "max": data[-1],
        "p25": percentile(data, 0.25),
        "p75": percentile(data, 0.75),
    }


def extract_metric(payload: Dict[str, Any], path: str) -> Optional[float]:
    current: Any = payload
    for key in path.split("."):
        if not isinstance(current, dict):
            return None
        current = current.get(key)
    return to_float(current)


def evaluate_file(path: Path, metric_paths: List[str]) -> Dict[str, Any]:
    payload = read_json(path)
    metrics: Dict[str, Optional[float]] = {}
    for metric_path in metric_paths:
        metrics[metric_path] = extract_metric(payload, metric_path)

    return {
        "file": str(path),
        "metrics": metrics,
    }


def collect_files(root: Path, pattern: str) -> List[Path]:
    return sorted(path for path in root.rglob(pattern) if path.is_file())


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Analyze all review.evaluation.json files under a directory and "
            "produce an aggregate report."
        )
    )
    parser.add_argument(
        "input_dir",
        nargs="?",
        default="reviews",
        help="Directory to recursively scan. Default: reviews",
    )
    parser.add_argument(
        "--pattern",
        default="*.evaluation.json",
        help="Filename glob pattern used during recursive scan. Default: *.evaluation.json",
    )
    parser.add_argument(
        "-o",
        "--output",
        default="exports/evaluation_analysis.json",
        help="Output report path. Default: exports/evaluation_analysis.json",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    input_dir = Path(args.input_dir)
    output_path = Path(args.output)

    if not input_dir.exists() or not input_dir.is_dir():
        raise SystemExit(f"Input directory not found: {input_dir}")

    metric_paths = [
        "truth_grounding.grounding_score",
        "truth_grounding.mentioned_entities",
        "truth_grounding.matched_entities",
        "review_alignment.comment_precision",
        "review_alignment.comment_recall",
        "review_alignment.comment_f1",
        "review_alignment.interpretation_precision",
        "review_alignment.interpretation_recall",
        "review_alignment.interpretation_f1",
        "quality_score.comprehensiveness",
        "quality_score.conciseness",
        "quality_score.relevance",
        "repetitive_rate",
        "meta.sentence_count",
        "meta.checklist_item_count",
        "meta.issue_count",
    ]

    files = collect_files(input_dir, args.pattern)
    records: List[Dict[str, Any]] = []
    skipped: List[Dict[str, str]] = []

    for path in files:
        try:
            records.append(evaluate_file(path, metric_paths))
        except Exception as exc:  # noqa: BLE001
            skipped.append({"file": str(path), "error": str(exc)})

    metric_values: Dict[str, List[float]] = {metric: [] for metric in metric_paths}
    for record in records:
        per_file_metrics = record["metrics"]
        for metric in metric_paths:
            value = per_file_metrics.get(metric)
            if isinstance(value, (int, float)):
                metric_values[metric].append(float(value))

    summary = {metric: summarize(metric_values[metric]) for metric in metric_paths}

    report = {
        "input_dir": str(input_dir),
        "pattern": args.pattern,
        "total_files_found": len(files),
        "total_files_parsed": len(records),
        "total_files_skipped": len(skipped),
        "summary": summary,
        "per_file": records,
        "skipped": skipped,
    }

    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_text = json.dumps(report, ensure_ascii=False, indent=2)
    output_path.write_text(output_text, encoding="utf-8")
    print(output_text)


if __name__ == "__main__":
    main()
