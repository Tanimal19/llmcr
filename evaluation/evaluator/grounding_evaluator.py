import re
from functools import lru_cache
from pathlib import Path
from dataclasses import dataclass, field
from typing import List, Set
from . import Evaluator
from share.utils import safe_div
from share.code_review_scheme import CodeReviewEntry
from share.pull_request_scheme import PullRequestEntry

SYMBOLS_FILE_PATH = Path(__file__).with_name("symbols.txt")
PASCAL_CASE_PATTERN = re.compile(r"\b[A-Z][a-z0-9]+(?:[A-Z][A-Za-z0-9]*)+\b")
IGNORED_ENTITIES = {
    "HashMap",
    "HashSet",
    "ArrayList",
}


@dataclass
class GroundingResult:
    hallucination_rate: float
    coverage_score: float
    mentioned_entities: float
    pr_entities: float
    mentioned_real_entities: float
    mentioned_pr_entities: float
    mentioned_entities_list: List[str] = field(default_factory=list)
    pr_entities_list: List[str] = field(default_factory=list)


def _normalize_identifier(value: str) -> str:
    """
    Normalize the extracted identifier by removing any trailing parentheses and non-alphanumeric characters.
    For example, "MyClassName(param)" will be normalized to "MyClassName".
    """

    text = (value or "").strip()
    if not text:
        return ""

    text = re.sub(r"\s*\([^)]*\)\s*$", "", text)
    match = re.search(r"[A-Za-z_][A-Za-z0-9_]*", text)
    return match.group(0) if match else ""


def _extract_entities(text: str) -> Set[str]:
    if not text or not text.strip():
        return set()

    entities: Set[str] = set()

    for match in PASCAL_CASE_PATTERN.findall(text):
        normalized = _normalize_identifier(match)
        if normalized:
            entities.add(normalized)

    return entities - IGNORED_ENTITIES


def _collect_review_text(review: CodeReviewEntry) -> str:
    fields = [
        review.motivation,
        review.good_points,
        review.bad_points,
        review.suggestion,
    ]
    for detail in review.implementation_details:
        fields.extend(detail.details)
    for issue in review.issues:
        fields.extend([issue.title, issue.location, issue.detail])
    return "\n".join(fields)


def _collect_pr_text(pr: PullRequestEntry) -> str:
    fields = [pr.title, pr.description]
    for files in pr.changed_files or []:
        fields.extend([files.path, files.patch, files.content])
    return "\n".join(fields)


@lru_cache(maxsize=1)
def _load_real_entities() -> Set[str]:
    entity_line_pattern = re.compile(r"^\s*(CLASS):\s*(.+?)\s*$")
    class_name_pattern = re.compile(r"[A-Z][a-z0-9]*(?:[A-Z][A-Za-z0-9]*)*")

    if not SYMBOLS_FILE_PATH.exists():
        return set()

    entities: Set[str] = set()
    for line in SYMBOLS_FILE_PATH.read_text(encoding="utf-8").splitlines():
        match = entity_line_pattern.match(line)
        if not match:
            continue

        symbol_name = (
            PASCAL_CASE_PATTERN.findall(match.group(2))[-1]
            if match.group(1) == "CLASS"
            else ""
        )
        if symbol_name:
            entities.add(symbol_name)

    return entities - IGNORED_ENTITIES


class GroundingEvaluator(Evaluator):
    def evaluate(
        self, review: CodeReviewEntry, pr: PullRequestEntry
    ) -> GroundingResult:

        real_entities = _load_real_entities()
        pr_entities = _extract_entities(_collect_pr_text(pr))
        mentioned_entities = _extract_entities(_collect_review_text(review))

        mentioned_pr = mentioned_entities & pr_entities
        mentioned_real = (mentioned_entities & real_entities) | mentioned_pr

        hallucination_rate = 1.0 - safe_div(
            len(mentioned_real), len(mentioned_entities)
        )
        coverage_score = safe_div(len(mentioned_pr), len(pr_entities))

        return GroundingResult(
            hallucination_rate=hallucination_rate,
            coverage_score=coverage_score,
            mentioned_entities=len(mentioned_entities),
            pr_entities=len(pr_entities),
            mentioned_real_entities=len(mentioned_real),
            mentioned_pr_entities=len(mentioned_pr),
            mentioned_entities_list=sorted(mentioned_entities),
            pr_entities_list=sorted(pr_entities),
        )
