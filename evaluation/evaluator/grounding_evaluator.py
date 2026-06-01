import re
from functools import lru_cache
from pathlib import Path
from typing import Any, Dict, List, Optional, Set
from utils import safe_div, clamp01
from evaluator.entity_extractor import extract_java_entities
from scheme import ParsedReview

SYMBOLS_FILE_PATH = Path(__file__).with_name("symbols.txt")

ENTITY_LINE_PATTERN = re.compile(r"^\s*(CLASS):\s*(.+?)\s*$")
CLASS_NAME_PATTERN = re.compile(r"[A-Z][a-z0-9]*(?:[A-Z][A-Za-z0-9]*)*")

IGNORED_ENTITIES = {
    "HashMap",
    "HashSet",
    "ArrayList",
}


def _collect_review_text_fields(parsed: ParsedReview) -> List[str]:
    text_fields = [
        getattr(parsed, field_name)
        for field_name in (
            "motivation",
            "good_points",
            "bad_points",
            "suggestion",
            "implementation_details",
            "static_analysis_results",
            "change_description",
            "change_motivation",
        )
    ]

    for issue in parsed.issues:
        text_fields.extend([issue.title, issue.location, issue.detail])

    for item in parsed.checklist_items:
        text_fields.extend([item.title, item.final_answer, item.analysis])
        for evidence in item.evidences:
            text_fields.extend([evidence.filepath, evidence.lines, evidence.reason])

    return text_fields


def extract_entities_from_review(parsed: ParsedReview) -> Set[str]:
    review_text = "\n".join(_collect_review_text_fields(parsed))
    return extract_java_entities(review_text)


def _build_pr_text(pr_entry: Dict[str, Any]) -> str:
    segments: List[str] = []
    for file_info in pr_entry.get("changed_files") or []:
        if isinstance(file_info, dict):
            for key in ("path", "patch", "content"):
                val = str(file_info.get(key) or "").strip()
                if val:
                    segments.append(val)
    diff = str(pr_entry.get("diff") or "").strip()
    if diff:
        segments.append(diff)
    return "\n".join(segments)


def extract_entities_from_pr(pr_entry: Optional[Dict[str, Any]]) -> Set[str]:
    if not pr_entry:
        return set()
    return extract_java_entities(_build_pr_text(pr_entry))


def _extract_class_name_from_symbol(raw: str) -> str:
    text = (raw or "").strip()
    if not text:
        return ""

    # Keep only PascalCase-like class names from symbols.txt class entries.
    matches = CLASS_NAME_PATTERN.findall(text)
    return matches[-1] if matches else ""


@lru_cache(maxsize=1)
def load_real_entities() -> Set[str]:
    if not SYMBOLS_FILE_PATH.exists():
        return set()

    entities: Set[str] = set()
    for line in SYMBOLS_FILE_PATH.read_text(encoding="utf-8").splitlines():
        match = ENTITY_LINE_PATTERN.match(line)
        if not match:
            continue

        symbol_name = _extract_class_name_from_symbol(match.group(2))
        if symbol_name:
            entities.add(symbol_name)

    return entities


def truth_grounding(
    parsed: ParsedReview, pr_entry: Optional[Dict[str, Any]]
) -> Dict[str, Any]:
    mentioned = extract_entities_from_review(parsed) - IGNORED_ENTITIES
    real_entities = load_real_entities() - IGNORED_ENTITIES
    pr_entities = extract_entities_from_pr(pr_entry) - IGNORED_ENTITIES

    mentioned_pr = mentioned & pr_entities
    mentioned_real = (mentioned & real_entities) | mentioned_pr

    hallucination_rate = 1.0 - safe_div(len(mentioned_real), len(mentioned))
    coverage_score = safe_div(len(mentioned_pr), len(pr_entities))

    return {
        "hallucination_rate": hallucination_rate,
        "coverage_score": coverage_score,
        "mentioned_entities": float(len(mentioned)),
        "pr_entities": float(len(pr_entities)),
        "mentioned_real_entities": float(len(mentioned_real)),
        "mentioned_pr_entities": float(len(mentioned_pr)),
        "mentioned_entities_list": list(mentioned),
        "pr_entities_list": list(pr_entities),
    }
