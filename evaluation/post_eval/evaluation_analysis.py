import json
from pathlib import Path
from statistics import median
from typing import Any, Dict, List, Optional, Union

DEFAULT_PATTERN = "review.evaluation.json"
METRIC_PATHS = [
    "truth_grounding.hallucination_rate",
    "truth_grounding.coverage_score",
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


def analysis(input_dir: Union[str, Path]) -> Dict[str, Any]:
    input_path = Path(input_dir).expanduser()

    if not input_path.exists() or not input_path.is_dir():
        raise ValueError(f"Input directory not found: {input_path}")

    files = collect_files(input_path, DEFAULT_PATTERN)
    records: List[Dict[str, Any]] = []
    skipped: List[Dict[str, str]] = []

    for path in files:
        try:
            records.append(evaluate_file(path, METRIC_PATHS))
        except Exception as exc:  # noqa: BLE001
            skipped.append({"file": str(path), "error": str(exc)})

    metric_values: Dict[str, List[float]] = {metric: [] for metric in METRIC_PATHS}
    for record in records:
        per_file_metrics = record["metrics"]
        for metric in METRIC_PATHS:
            value = per_file_metrics.get(metric)
            if isinstance(value, (int, float)):
                metric_values[metric].append(float(value))

    summary = {metric: summarize(metric_values[metric]) for metric in METRIC_PATHS}

    return {
        "input_dir": str(input_path),
        "pattern": DEFAULT_PATTERN,
        "total_files_found": len(files),
        "total_files_parsed": len(records),
        "total_files_skipped": len(skipped),
        "summary": summary,
        "per_file": records,
        "skipped": skipped,
    }


def report_to_json(report: Dict[str, Any]) -> str:
    return json.dumps(report, ensure_ascii=False, indent=2)
