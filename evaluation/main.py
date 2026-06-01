import json
import re
import sys
from difflib import SequenceMatcher
from pathlib import Path
from typing import Any, Dict, List, Optional, Set, Tuple, cast
from scheme import ChecklistEvidence, ChecklistItem, Issue, ParsedReview

from evaluator.entity_extractor import (
    extract_java_entities,
)
from evaluator.llm_as_judge import judge_quality_score
from evaluator.sentence_bert_alignment import (
    is_sentence_bert_available,
    sentence_bert_sentence_alignment,
)

SBERT_MODEL_NAME = "all-MiniLM-L6-v2"
REPETITIVE_THRESHOLD = 0.9


def safe_div(numerator: float, denominator: float) -> float:
    if denominator == 0:
        return 0.0
    return numerator / denominator


def clamp01(value: float) -> float:
    return max(0.0, min(1.0, value))


def clamp15(value: float) -> int:
    return int(max(1.0, min(5.0, round(value))))


def word_count(text: str) -> int:
    return len(re.findall(r"\b\w+\b", text or ""))


def split_sentences(text: str) -> List[str]:
    chunks = re.split(r"(?<=[.!?。！？])\s+|\n+", text or "")
    return [chunk.strip() for chunk in chunks if chunk.strip()]


def sentence_similarity(a: str, b: str) -> float:
    return SequenceMatcher(None, (a or "").lower(), (b or "").lower()).ratio()


def compute_repetitive_rate(sentences: List[str], threshold: float = 0.9) -> float:
    if not sentences:
        return 0.0

    clusters: List[List[str]] = []
    for sentence in sentences:
        placed = False
        for cluster in clusters:
            if sentence_similarity(sentence, cluster[0]) >= threshold:
                cluster.append(sentence)
                placed = True
                break
        if not placed:
            clusters.append([sentence])

    return 1.0 - safe_div(len(clusters), len(sentences))


def to_text(value: Any) -> str:
    if value is None:
        return ""
    return str(value)


def to_string_list(value: Any) -> List[str]:
    if not isinstance(value, list):
        return []
    return [str(item).strip() for item in value if str(item).strip()]


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


def extract_entities_from_review(parsed: ParsedReview) -> Set[str]:
    text_fields = [
        parsed.motivation,
        parsed.good_points,
        parsed.bad_points,
        parsed.suggestion,
        parsed.implementation_details,
        parsed.static_analysis_results,
        parsed.change_description,
        parsed.change_motivation,
    ]
    for issue in parsed.issues:
        text_fields.extend([issue.title, issue.location, issue.detail])
    for item in parsed.checklist_items:
        text_fields.extend([item.title, item.final_answer, item.analysis])
        for evidence in item.evidences:
            text_fields.extend([evidence.filepath, evidence.lines, evidence.reason])

    return extract_java_entities("\n".join(text_fields))


def build_pr_text(pr_entry: Dict[str, Any]) -> str:
    segments: List[str] = []
    for file_info in pr_entry.get("changed_files") or []:
        if isinstance(file_info, dict):
            for key in ("path", "patch", "content"):
                val = str(file_info.get(key) or "").strip()
                if val:
                    segments.append(val)
    diff = str(pr_entry.get("diff") or pr_entry.get("Diff") or "").strip()
    if diff:
        segments.append(diff)
    return "\n".join(segments)


def entities_matched_in_text(entities: Set[str], text: str) -> Set[str]:
    return {e for e in entities if e in text}


def truth_grounding(
    parsed: ParsedReview, pr_entry: Optional[Dict[str, Any]]
) -> Dict[str, float]:
    mentioned = extract_entities_from_review(parsed)
    pr_text = build_pr_text(pr_entry) if pr_entry else ""
    matched = entities_matched_in_text(mentioned, pr_text)
    grounding_score = safe_div(len(matched), len(mentioned))

    return {
        "grounding_score": clamp01(grounding_score),
        "mentioned_entities": float(len(mentioned)),
        "matched_entities": float(len(matched)),
    }


def truth_grounding_details(
    parsed: ParsedReview, pr_entry: Optional[Dict[str, Any]]
) -> Dict[str, Any]:
    mentioned = sorted(extract_entities_from_review(parsed))
    pr_text = build_pr_text(pr_entry) if pr_entry else ""
    matched = sorted(entities_matched_in_text(set(mentioned), pr_text))
    grounding_score = clamp01(safe_div(len(matched), len(mentioned)))

    return {
        "grounding_score": grounding_score,
        "mentioned_entities": float(len(mentioned)),
        "matched_entities": float(len(matched)),
        "mentioned_entities_list": mentioned,
        "matched_entities_list": matched,
    }


def collect_comment_candidates(parsed: ParsedReview) -> List[str]:
    candidate_text = "\n".join(
        [
            parsed.good_points,
            parsed.bad_points,
            parsed.suggestion,
            "\n".join(f"{issue.title}. {issue.detail}" for issue in parsed.issues),
        ]
    )
    return split_sentences(candidate_text)


def collect_comment_references(pr_entry: Optional[Dict[str, Any]]) -> List[str]:
    if not pr_entry:
        return []

    return to_string_list(pr_entry.get("normalized_review_sentences"))


def collect_interpretation_candidates(parsed: ParsedReview) -> List[str]:
    return split_sentences(
        "\n".join([parsed.change_description, parsed.change_motivation])
    )


def collect_interpretation_references(pr_entry: Optional[Dict[str, Any]]) -> List[str]:
    if not pr_entry:
        return []

    return to_string_list(pr_entry.get("normalized_description_sentences"))


def review_alignment(
    parsed: ParsedReview,
    pr_entry: Optional[Dict[str, Any]],
    sentence_bert_model_name: str = SBERT_MODEL_NAME,
) -> Dict[str, float]:
    comment_refs = collect_comment_references(pr_entry)
    comment_cands = collect_comment_candidates(parsed)

    interp_refs = collect_interpretation_references(pr_entry)
    interp_cands = collect_interpretation_candidates(parsed)

    cp, cr, cf1 = sentence_bert_sentence_alignment(
        comment_refs,
        comment_cands,
        sentence_bert_model_name,
    )
    ip, ir, if1 = sentence_bert_sentence_alignment(
        interp_refs,
        interp_cands,
        sentence_bert_model_name,
    )

    return {
        "comment_precision": clamp01(cp),
        "comment_recall": clamp01(cr),
        "comment_f1": clamp01(cf1),
        "interpretation_precision": clamp01(ip),
        "interpretation_recall": clamp01(ir),
        "interpretation_f1": clamp01(if1),
    }


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
    sentence_bert_model_name: str = SBERT_MODEL_NAME,
) -> Dict[str, Any]:
    parsed = parse_review_json(review_payload)
    grounding_details = truth_grounding_details(parsed, pr_entry)
    grounding = {
        "grounding_score": float(grounding_details["grounding_score"]),
        "mentioned_entities": float(grounding_details["mentioned_entities"]),
        "matched_entities": float(grounding_details["matched_entities"]),
    }

    comment_refs = collect_comment_references(pr_entry)
    comment_cands = collect_comment_candidates(parsed)
    interp_refs = collect_interpretation_references(pr_entry)
    interp_cands = collect_interpretation_candidates(parsed)

    alignment = review_alignment(
        parsed,
        pr_entry,
        sentence_bert_model_name=sentence_bert_model_name,
    )

    review_text = build_review_text(parsed)
    all_sentences = split_sentences(review_text)
    repetitive = clamp01(
        compute_repetitive_rate(all_sentences, threshold=REPETITIVE_THRESHOLD)
    )
    quality = judge_quality_score(
        parsed_review=serialize_parsed_review(parsed),
        pr_entry=pr_entry,
        static_analysis_results=parsed.static_analysis_results,
    )

    return {
        "truth_grounding": grounding,
        "review_alignment": alignment,
        "quality_score": quality,
        "repetitive_rate": repetitive,
        "meta": {
            "sentence_count": len(all_sentences),
            "checklist_item_count": len(parsed.checklist_items),
            "issue_count": len(parsed.issues),
        },
        "details": {
            "parsed_review": serialize_parsed_review(parsed),
            "truth_grounding": {
                "mentioned_entities_list": grounding_details["mentioned_entities_list"],
                "matched_entities_list": grounding_details["matched_entities_list"],
            },
            "review_alignment": {
                "comment_references": comment_refs,
                "comment_candidates": comment_cands,
                "interpretation_references": interp_refs,
                "interpretation_candidates": interp_cands,
            },
        },
    }


def load_json(path: Path) -> Dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def load_jsonl_first(path: Path) -> Dict[str, Any]:
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if stripped:
            return json.loads(stripped)
    return {}


def to_int(value: Any) -> Optional[int]:
    if isinstance(value, bool):
        return None
    if isinstance(value, int):
        return value
    if isinstance(value, str):
        text = value.strip()
        if text and re.fullmatch(r"-?\d+", text):
            return int(text)
    return None


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
