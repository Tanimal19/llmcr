import json
import re
import sys
from pathlib import Path
from typing import Any, Dict, List, Optional, Set, Tuple, cast
from scheme import ChecklistEvidence, ChecklistItem, Issue, ParsedReview

from utils import *
from evaluator.grounding_evaluator import truth_grounding
from evaluator.llm_as_judge import judge_quality_score
from evaluator.repetitive_evaluator import compute_repetitive_rate
from evaluator.alignment_evaluator import review_alignment


def parse_issues_json(raw_issues: Any) -> List[Issue]:
    if not isinstance(raw_issues, list):
        return []

    issues: List[Issue] = []
    for raw in raw_issues:
        if not isinstance(raw, dict):
            continue
        issues.append(
            Issue(
                issue_type=to_text(raw.get("type")).strip(),
                title=to_text(raw.get("title")).strip(),
                location=to_text(raw.get("location")).strip(),
                detail=to_text(raw.get("detail")).strip(),
            )
        )
    return issues


def format_implementation_details(raw_details: Any) -> str:
    if not isinstance(raw_details, list):
        return ""

    lines: List[str] = []
    for entry in raw_details:
        if not isinstance(entry, dict):
            continue
        filename = to_text(entry.get("filename")).strip() or "(unknown file)"
        details = to_string_list(entry.get("details"))
        if details:
            detail_text = " ".join(details)
            lines.append(f"{filename}: {detail_text}")
        else:
            lines.append(filename)
    return "\n".join(lines)


def parse_checklist_items_json(raw_items: Any) -> List[ChecklistItem]:
    if not isinstance(raw_items, list):
        return []

    items: List[ChecklistItem] = []
    for index, raw_item in enumerate(raw_items, 1):
        if not isinstance(raw_item, dict):
            continue

        title = to_text(raw_item.get("title")).strip() or str(index)
        answer = (
            raw_item.get("answer") if isinstance(raw_item.get("answer"), dict) else {}
        )
        evidences_raw = answer.get("evidence") if isinstance(answer, dict) else []

        evidences: List[ChecklistEvidence] = []
        if isinstance(evidences_raw, list):
            for evidence in evidences_raw:
                if not isinstance(evidence, dict):
                    continue
                evidences.append(
                    ChecklistEvidence(
                        filepath=to_text(evidence.get("file")).strip(),
                        lines=to_text(evidence.get("lines"))
                        .replace("~", "-")
                        .replace(" ", ""),
                        reason=to_text(evidence.get("reason")).strip(),
                    )
                )

        items.append(
            ChecklistItem(
                title=title,
                final_answer=to_text(
                    answer.get("finalAnswer") if isinstance(answer, dict) else ""
                ).strip(),
                analysis=to_text(
                    answer.get("analysis") if isinstance(answer, dict) else ""
                ).strip(),
                evidences=evidences,
                final_answer_labeled=True,
                analysis_labeled=True,
                expected_evidence_count=len(evidences),
            )
        )

    return items


def unwrap_review_payload(payload: Dict[str, Any]) -> Dict[str, Any]:
    if isinstance(payload.get("reviewReport"), dict):
        return cast(Dict[str, Any], payload["reviewReport"])
    return payload


def parse_review_json(payload: Dict[str, Any]) -> ParsedReview:
    review = unwrap_review_payload(payload)
    raw_content = review.get("content")
    content: Dict[str, Any] = raw_content if isinstance(raw_content, dict) else {}
    raw_interpretation = review.get("interpretation")
    interpretation: Dict[str, Any] = (
        raw_interpretation if isinstance(raw_interpretation, dict) else {}
    )

    good_points = to_string_list(content.get("goodPoints"))
    bad_points = to_string_list(content.get("badPoints"))

    return ParsedReview(
        motivation=to_text(content.get("motivation")).strip(),
        good_points="\n".join(good_points),
        bad_points="\n".join(bad_points),
        suggestion=to_text(content.get("suggestion")).strip(),
        implementation_details=format_implementation_details(
            content.get("implementationDetails")
        ),
        issues=parse_issues_json(content.get("issues")),
        static_analysis_results=to_text(
            review.get("staticAnalysisResults") or review.get("static_analysis_results")
        ).strip(),
        change_description=to_text(interpretation.get("changeDescription")).strip(),
        change_motivation=to_text(interpretation.get("changeMotivation")).strip(),
        checklist_items=parse_checklist_items_json(review.get("checklistItems")),
    )


def build_review_text(parsed: ParsedReview) -> str:
    checklist_text = "\n".join(
        "\n".join([item.title, item.final_answer, item.analysis])
        for item in parsed.checklist_items
    )
    issues_text = "\n".join(
        f"{issue.issue_type} {issue.title} {issue.location} {issue.detail}".strip()
        for issue in parsed.issues
    )

    return "\n".join(
        [
            parsed.motivation,
            parsed.good_points,
            parsed.bad_points,
            parsed.suggestion,
            parsed.implementation_details,
            issues_text,
            parsed.static_analysis_results,
            parsed.change_description,
            parsed.change_motivation,
            checklist_text,
        ]
    )


def normalize_line_span(text: str) -> str:
    cleaned = re.sub(r"\s+", "", (text or "").replace("~", "-"))
    match = re.fullmatch(r"(\d+)-(\d+)", cleaned)
    if not match:
        return ""
    start = int(match.group(1))
    end = int(match.group(2))
    if end < start:
        start, end = end, start
    return f"{start}-{end}"


def extract_changed_file_paths(pr_entry: Optional[Dict[str, Any]]) -> Set[str]:
    if not pr_entry:
        return set()
    changed_files = pr_entry.get("changed_files") or []
    paths = set()
    for file_info in changed_files:
        if isinstance(file_info, dict):
            path = file_info.get("path")
            if path:
                paths.add(path)
    if not paths:
        diff_text = pr_entry.get("diff") or pr_entry.get("Diff") or ""
        paths.update(re.findall(r"\+\+\+\s+b/([^\n]+)", diff_text))
    return paths


def serialize_parsed_review(parsed: ParsedReview) -> Dict[str, Any]:
    return {
        "motivation": parsed.motivation,
        "good_points": parsed.good_points,
        "bad_points": parsed.bad_points,
        "suggestion": parsed.suggestion,
        "implementation_details": parsed.implementation_details,
        "issues": [
            {
                "type": issue.issue_type,
                "title": issue.title,
                "location": issue.location,
                "detail": issue.detail,
            }
            for issue in parsed.issues
        ],
    }


def evaluate_review(
    review_payload: Dict[str, Any],
    pr_entry: Dict[str, Any],
) -> Dict[str, Any]:
    parsed_review = parse_review_json(review_payload)
    grounding = truth_grounding(parsed_review, pr_entry)

    alignment = review_alignment(parsed_review, pr_entry)

    review_text = build_review_text(parsed_review)
    all_sentences = split_sentences(review_text)
    repetitive = clamp01(compute_repetitive_rate(all_sentences))
    quality = judge_quality_score(
        parsed_review=serialize_parsed_review(parsed_review),
        pr_entry=pr_entry,
        static_analysis_results=parsed_review.static_analysis_results,
    )

    return {
        "pr_id": extract_review_pr_id(review_payload),
        "truth_grounding": grounding,
        "review_alignment": alignment,
        "quality_score": quality,
        "repetitive_rate": repetitive,
        "meta": {
            "sentence_count": len(all_sentences),
            "checklist_item_count": len(parsed_review.checklist_items),
            "issue_count": len(parsed_review.issues),
            "review": parsed_review,
            "pull_request": pr_entry,
        },
    }


def extract_review_pr_id(review_payload: Dict[str, Any]) -> Optional[int]:
    review = unwrap_review_payload(review_payload)
    return to_int(review.get("prId")) or to_int(review.get("pr_id"))


def extract_pr_id_from_entry(entry: Dict[str, Any]) -> Optional[int]:
    return to_int(entry.get("pr_id")) or to_int(entry.get("prId"))


def load_pr_entry(path: Path, review_payload: Dict[str, Any]) -> Dict[str, Any]:
    if not path.exists():
        raise FileNotFoundError(f"PR file not found: {path}")

    if path.suffix.lower() != ".jsonl":
        raise ValueError(f"PR file must be .jsonl: {path}")

    review_pr_id = extract_review_pr_id(review_payload)
    if review_pr_id is None:
        raise ValueError("Review payload must contain prId (or pr_id)")

    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped:
            continue

        entry = json.loads(stripped)
        if not isinstance(entry, dict):
            continue

        entry_pr_id = extract_pr_id_from_entry(entry)
        if entry_pr_id == review_pr_id:
            return entry

    raise ValueError(f"No matching PR found for prId={review_pr_id} in {path}")


def load_review_payload(path: Path) -> Dict[str, Any]:
    if not path.exists():
        raise FileNotFoundError(f"Review file not found: {path}")
    if path.suffix.lower() == ".jsonl":
        payload = load_jsonl_first(path)
    else:
        payload = load_json(path)
    if not isinstance(payload, dict):
        raise ValueError(f"Review file must contain a JSON object: {path}")
    return payload


def parse_cli_args(argv: List[str]) -> Tuple[Path, Path, Path]:
    if len(argv) not in (3, 4):
        raise SystemExit(
            "Usage: python evaluation.py [input_review.json] [input_pr.jsonl] [output.json]"
        )

    review_path = Path(argv[1])
    pr_path = Path(argv[2])
    if len(argv) == 4:
        output_path = Path(argv[3])
    else:
        output_path = review_path.with_suffix(".evaluation.json")

    return review_path, pr_path, output_path


def main() -> None:
    review_path, pr_path, output_path = parse_cli_args(sys.argv)
    review_payload = load_review_payload(review_path)
    pr_entry = load_pr_entry(pr_path, review_payload)

    result = evaluate_review(
        review_payload,
        pr_entry,
    )
    output_text = json.dumps(result, ensure_ascii=False, indent=2)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(output_text, encoding="utf-8")
    print(output_text)


if __name__ == "__main__":
    main()
