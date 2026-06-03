import re
from difflib import SequenceMatcher
from typing import List
from . import Evaluator
from share.utils import safe_div
from share.code_review_scheme import CodeReviewEntry
from share.pull_request_scheme import PullRequestEntry

REPETITIVE_THRESHOLD = 0.9


def _split_sentences(text: str) -> List[str]:
    chunks = re.split(r"(?<=[.!?。！？])\s+|\n+", text or "")
    return [chunk.strip() for chunk in chunks if chunk.strip()]


def _collect_sentences(review: CodeReviewEntry) -> List[str]:
    sentences = []
    for field in [
        review.motivation,
        review.good_points,
        review.bad_points,
        review.suggestion,
    ]:
        sentences.extend(_split_sentences(field))
    for issue in review.issues:
        sentences.append(f"{issue.title}: {issue.detail}")
    for impl in review.implementation_details:
        sentences.append(f"{impl.filename}: {'; '.join(impl.details)}")
    return sentences


def _sentence_similarity(a: str, b: str) -> float:
    return SequenceMatcher(None, (a or "").lower(), (b or "").lower()).ratio()


class RepetitiveEvaluator(Evaluator):
    def evaluate(self, review: CodeReviewEntry, pr: PullRequestEntry) -> float:
        sentences = _collect_sentences(review)
        clusters: List[List[str]] = []
        for sentence in sentences:
            placed = False
            for cluster in clusters:
                if _sentence_similarity(sentence, cluster[0]) >= REPETITIVE_THRESHOLD:
                    cluster.append(sentence)
                    placed = True
                    break
            if not placed:
                clusters.append([sentence])

        return 1.0 - safe_div(len(clusters), len(sentences))
