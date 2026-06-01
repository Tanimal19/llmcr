import json
from dataclasses import asdict
from pathlib import Path
from statistics import median
from typing import Any, Dict, List, Optional, Union

from scheme import (
    ChangedFileEntry,
    ChecklistEvidence,
    ChecklistItem,
    CommentEntry,
    EvaluationMeta,
    EvaluationResult,
    Issue,
    ParsedReview,
    PullRequestEntry,
    QualityScore,
    ReviewAlignment,
    TruthGrounding,
)

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
    "meta.pull_request.changed_files_count",
]


def to_int(value: Any, default: int = 0) -> int:
    if isinstance(value, bool):
        return default
    if isinstance(value, int):
        return value
    if isinstance(value, float):
        return int(value)
    if isinstance(value, str):
        text = value.strip()
        if not text:
            return default
        try:
            return int(float(text))
        except ValueError:
            return default
    return default


def to_bool(value: Any, default: bool = False) -> bool:
    if isinstance(value, bool):
        return value
    if isinstance(value, str):
        text = value.strip().lower()
        if text in {"true", "1", "yes", "y", "on"}:
            return True
        if text in {"false", "0", "no", "n", "off"}:
            return False
    return default


def to_text(value: Any, default: str = "") -> str:
    if value is None:
        return default
    if isinstance(value, str):
        return value
    return str(value)


def to_string_list(value: Any) -> List[str]:
    if not isinstance(value, list):
        return []
    return [to_text(item).strip() for item in value if to_text(item).strip()]


def to_dict(value: Any) -> Dict[str, Any]:
    return value if isinstance(value, dict) else {}


def to_list(value: Any) -> List[Any]:
    return value if isinstance(value, list) else []


def parse_comment_entry(payload: Dict[str, Any]) -> CommentEntry:
    return CommentEntry(
        type=to_text(payload.get("type")).strip(),
        poster=to_text(payload.get("poster")).strip() or None,
        created_at=to_text(payload.get("created_at")).strip() or None,
        body=to_text(payload.get("body")).strip(),
        state=to_text(payload.get("state")).strip() or None,
    )


def parse_changed_file_entry(payload: Dict[str, Any]) -> ChangedFileEntry:
    return ChangedFileEntry(
        path=to_text(payload.get("path")).strip(),
        previous_path=to_text(payload.get("previous_path")).strip() or None,
        patch=to_text(payload.get("patch")),
        content=to_text(payload.get("content")),
    )


def parse_pull_request_entry(payload: Dict[str, Any]) -> PullRequestEntry:
    comments = [
        parse_comment_entry(item)
        for item in to_list(payload.get("comments"))
        if isinstance(item, dict)
    ]
    changed_files = [
        parse_changed_file_entry(item)
        for item in to_list(payload.get("changed_files"))
        if isinstance(item, dict)
    ]

    return PullRequestEntry(
        pr_id=to_int(payload.get("pr_id") or payload.get("prId")),
        url=to_text(payload.get("url")).strip() or None,
        title=to_text(payload.get("title")).strip(),
        pr_description=to_text(
            payload.get("pr_description") or payload.get("description")
        ).strip(),
        is_closed=to_bool(payload.get("is_closed")),
        is_merged=to_bool(payload.get("is_merged")),
        is_approved=to_bool(payload.get("is_approved")),
        comments=comments,
        changed_files=changed_files,
        changed_files_count=to_int(
            payload.get("changed_files_count"), len(changed_files)
        ),
    )


def parse_checklist_evidence(payload: Dict[str, Any]) -> ChecklistEvidence:
    return ChecklistEvidence(
        filepath=to_text(payload.get("filepath") or payload.get("file")).strip(),
        lines=to_text(payload.get("lines")).strip(),
        reason=to_text(payload.get("reason")).strip(),
    )


def parse_checklist_item(payload: Dict[str, Any]) -> ChecklistItem:
    evidences = [
        parse_checklist_evidence(item)
        for item in to_list(payload.get("evidences") or payload.get("evidence"))
        if isinstance(item, dict)
    ]
    return ChecklistItem(
        title=to_text(payload.get("title")).strip(),
        final_answer=to_text(
            payload.get("final_answer") or payload.get("finalAnswer")
        ).strip(),
        analysis=to_text(payload.get("analysis")).strip(),
        evidences=evidences,
        final_answer_labeled=to_bool(payload.get("final_answer_labeled"), False),
        analysis_labeled=to_bool(payload.get("analysis_labeled"), False),
        expected_evidence_count=to_int(
            payload.get("expected_evidence_count"), len(evidences)
        ),
    )


def parse_issue(payload: Dict[str, Any]) -> Issue:
    return Issue(
        issue_type=to_text(payload.get("issue_type") or payload.get("type")).strip(),
        title=to_text(payload.get("title")).strip(),
        location=to_text(payload.get("location")).strip(),
        detail=to_text(payload.get("detail")).strip(),
    )


def parse_parsed_review(payload: Dict[str, Any]) -> ParsedReview:
    issues = [
        parse_issue(item)
        for item in to_list(payload.get("issues"))
        if isinstance(item, dict)
    ]
    checklist_items = [
        parse_checklist_item(item)
        for item in to_list(payload.get("checklist_items"))
        if isinstance(item, dict)
    ]

    return ParsedReview(
        motivation=to_text(payload.get("motivation")).strip(),
        good_points=to_text(payload.get("good_points")).strip(),
        bad_points=to_text(payload.get("bad_points")).strip(),
        suggestion=to_text(payload.get("suggestion")).strip(),
        implementation_details=to_text(payload.get("implementation_details")).strip(),
        issues=issues,
        static_analysis_results=to_text(payload.get("static_analysis_results")).strip(),
        change_description=to_text(payload.get("change_description")).strip(),
        change_motivation=to_text(payload.get("change_motivation")).strip(),
        checklist_items=checklist_items,
    )


def parse_truth_grounding(payload: Dict[str, Any]) -> TruthGrounding:
    return TruthGrounding(
        hallucination_rate=to_float(payload.get("hallucination_rate")) or 0.0,
        coverage_score=to_float(payload.get("coverage_score")) or 0.0,
        mentioned_entities=to_float(payload.get("mentioned_entities")) or 0.0,
        pr_entities=to_float(payload.get("pr_entities")) or 0.0,
        mentioned_real_entities=to_float(payload.get("mentioned_real_entities")) or 0.0,
        mentioned_pr_entities=to_float(payload.get("mentioned_pr_entities")) or 0.0,
        mentioned_entities_list=to_string_list(payload.get("mentioned_entities_list")),
        pr_entities_list=to_string_list(payload.get("pr_entities_list")),
    )


def parse_review_alignment(payload: Dict[str, Any]) -> ReviewAlignment:
    return ReviewAlignment(
        comment_precision=to_float(payload.get("comment_precision")) or 0.0,
        comment_recall=to_float(payload.get("comment_recall")) or 0.0,
        comment_f1=to_float(payload.get("comment_f1")) or 0.0,
        interpretation_precision=to_float(payload.get("interpretation_precision"))
        or 0.0,
        interpretation_recall=to_float(payload.get("interpretation_recall")) or 0.0,
        interpretation_f1=to_float(payload.get("interpretation_f1")) or 0.0,
        comment_refs=to_string_list(payload.get("comment_refs")),
        comment_cands=to_string_list(payload.get("comment_cands")),
        interp_refs=to_string_list(payload.get("interp_refs")),
        interp_cands=to_string_list(payload.get("interp_cands")),
    )


def parse_quality_score(payload: Dict[str, Any]) -> QualityScore:
    judge_full_output = to_dict(payload.get("judge_full_output"))
    topics = payload.get("topics_to_be_covered")
    if not isinstance(topics, list):
        topics = judge_full_output.get("topics_to_be_covered")

    steps = payload.get("step_by_step_analysis")
    if not isinstance(steps, list):
        steps = judge_full_output.get("step_by_step_analysis")

    rationale = to_text(payload.get("rationale")).strip()
    if not rationale:
        rationale = to_text(judge_full_output.get("rationale")).strip()

    return QualityScore(
        comprehensiveness=to_int(payload.get("comprehensiveness"), 0),
        conciseness=to_int(payload.get("conciseness"), 0),
        relevance=to_int(payload.get("relevance"), 0),
        topics_to_be_covered=to_string_list(topics),
        step_by_step_analysis=to_string_list(steps),
        rationale=rationale,
    )


def parse_evaluation_meta(payload: Dict[str, Any]) -> EvaluationMeta:
    review_payload = to_dict(payload.get("review"))
    pr_payload = to_dict(payload.get("pull_request"))
    return EvaluationMeta(
        sentence_count=to_int(payload.get("sentence_count"), 0),
        checklist_item_count=to_int(payload.get("checklist_item_count"), 0),
        issue_count=to_int(payload.get("issue_count"), 0),
        review=parse_parsed_review(review_payload),
        pull_request=parse_pull_request_entry(pr_payload),
    )


def parse_evaluation_result(payload: Dict[str, Any]) -> EvaluationResult:
    return EvaluationResult(
        pr_id=to_int(payload.get("pr_id") or payload.get("prId"), 0),
        truth_grounding=parse_truth_grounding(to_dict(payload.get("truth_grounding"))),
        review_alignment=parse_review_alignment(
            to_dict(payload.get("review_alignment"))
        ),
        quality_score=parse_quality_score(to_dict(payload.get("quality_score"))),
        repetitive_rate=to_float(payload.get("repetitive_rate")) or 0.0,
        meta=parse_evaluation_meta(to_dict(payload.get("meta"))),
    )


def metric_value(result: EvaluationResult, path: str) -> Optional[float]:
    current: Any = result
    for key in path.split("."):
        if not hasattr(current, key):
            return None
        current = getattr(current, key)
    return to_float(current)


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
    evaluation = parse_evaluation_result(payload)
    metrics: Dict[str, Optional[float]] = {}
    for metric_path in metric_paths:
        metrics[metric_path] = metric_value(evaluation, metric_path)

    return {
        "file": str(path),
        "pr_id": evaluation.pr_id,
        "evaluation": evaluation,
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
    json_ready = dict(report)
    if "per_file" in json_ready and isinstance(json_ready["per_file"], list):
        serialized: List[Dict[str, Any]] = []
        for item in json_ready["per_file"]:
            if not isinstance(item, dict):
                continue
            copied = dict(item)
            evaluation = copied.get("evaluation")
            if isinstance(evaluation, EvaluationResult):
                copied["evaluation"] = asdict(evaluation)
            serialized.append(copied)
        json_ready["per_file"] = serialized

    return json.dumps(json_ready, ensure_ascii=False, indent=2)
