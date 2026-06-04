import re
from functools import lru_cache
from pathlib import Path
from dataclasses import dataclass, field
from typing import List, Set
from . import Evaluator
from .entity_extractor import extract_entities_regex
from share.utils import safe_div
from share.code_review_scheme import CodeReviewEntry, CodeReviewContent
from share.pull_request_scheme import PullRequestEntry

@dataclass
class GroundingResult:
    hallucination_rate: float
    coverage_score: float
    mentioned_entities: float
    pr_entities: float
    mentioned_pr_entities: float
    mentioned_entities_list: List[str] = field(default_factory=list)
    pr_entities_list: List[str] = field(default_factory=list)




def _collect_review_text(review: CodeReviewContent) -> str:
    fields = [
        review.motivation,
        review.suggestion,
    ]
    fields.extend(review.good_points)
    fields.extend(review.bad_points)
    for impl in review.implementation_details:
        fields.extend(impl.filename)
        fields.extend(impl.details)
    for issue in review.issues:
        fields.extend([issue.title, issue.location, issue.detail])
    return "\n".join(fields)


def _collect_pr_text(pr: PullRequestEntry) -> str:
    fields = [pr.title, pr.description]
    for files in pr.changed_files or []:
        fields.extend([files.path, files.patch, files.content])
    for comment in pr.comments or []:
        fields.extend([comment.body, comment.diff_content or ""])
    return "\n".join(fields)


class GroundingEvaluator(Evaluator):
    def evaluate(
        self, review: CodeReviewEntry, pr: PullRequestEntry
    ) -> GroundingResult:

        pr_entities = extract_entities_regex(_collect_pr_text(pr))
        mentioned_entities = extract_entities_regex(_collect_review_text(review.content))

        mentioned_pr = mentioned_entities & pr_entities

        hallucination_rate = 1.0 - safe_div(len(mentioned_pr), len(mentioned_entities))
        coverage_score = safe_div(len(mentioned_pr), len(pr_entities))

        return GroundingResult(
            hallucination_rate=hallucination_rate,
            coverage_score=coverage_score,
            mentioned_entities=len(mentioned_entities),
            pr_entities=len(pr_entities),
            mentioned_pr_entities=len(mentioned_pr),
            mentioned_entities_list=sorted(mentioned_entities),
            pr_entities_list=sorted(pr_entities),
        )
