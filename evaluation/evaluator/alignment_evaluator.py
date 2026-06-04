import torch
import re
from typing import List, Tuple
from dataclasses import dataclass, field
from numbers import Integral
from bert_score import BERTScorer
from . import Evaluator
from share.code_review_scheme import CodeReviewEntry, CodeReviewContent
from share.pull_request_scheme import PullRequestEntry
from share.utils import (
    clamp01,
    safe_div,
    get_filename_from_pathstring,
)

BERTSCORE_LANG = "en"
BERTSCORE_MODEL_TYPE = "roberta-large"
TOKENIZER_MAX_LENGTH = 512
_SCORER_CACHE: dict[Tuple[str, str], BERTScorer] = {}


@dataclass
class AlignmentResult:
    comment_precision: float
    comment_recall: float
    comment_f1: float
    interpretation_precision: float
    interpretation_recall: float
    interpretation_f1: float
    comment_refs: List[str] = field(default_factory=list)
    comment_cands: List[str] = field(default_factory=list)
    interp_refs: List[str] = field(default_factory=list)
    interp_cands: List[str] = field(default_factory=list)


def _split_sentences(text: str) -> List[str]:
    chunks = re.split(r"(?<=[.!?。！？])\s+|\n+", text or "")
    return [chunk.strip() for chunk in chunks if chunk.strip()]


def _collect_comment_candidates(review: CodeReviewContent) -> List[str]:
    candidate_text = "\n".join(
        [
            review.suggestion,
            "\n".join(review.good_points),
            "\n".join(review.bad_points),
            "\n".join(f"{issue.title}: {issue.detail}" for issue in review.issues),
        ]
    )
    return _split_sentences(candidate_text)


def _collect_comment_references(pr: PullRequestEntry) -> List[str]:
    return pr.rephrased_comments or []


def _collect_interpretation_candidates(review: CodeReviewContent) -> List[str]:
    candidate_text = "\n".join(
        [
            review.motivation,
            "\n".join(
                f"{get_filename_from_pathstring(impl.filename)}: {detail}"
                for impl in review.implementation_details
                for detail in impl.details
            ),
        ]
    )
    return _split_sentences(candidate_text)


def _collect_interpretation_references(pr: PullRequestEntry) -> List[str]:
    return pr.rephrased_description or []


def _get_scorer(lang: str, model_type: str) -> BERTScorer:
    device = "cuda" if torch.cuda.is_available() else "cpu"
    cache_key = (model_type, device)
    cached = _SCORER_CACHE.get(cache_key)
    if cached is not None:
        return cached

    scorer = BERTScorer(
        lang=lang,
        model_type=model_type,
        device=device,
        rescale_with_baseline=True,
    )
    tokenizer = getattr(scorer, "_tokenizer", None)
    if tokenizer is not None:
        current_max_length = getattr(tokenizer, "model_max_length", None)
        if (
            not isinstance(current_max_length, Integral)
            or int(current_max_length) <= 0
            or int(current_max_length) > 100000
        ):
            tokenizer.model_max_length = TOKENIZER_MAX_LENGTH

    _SCORER_CACHE[cache_key] = scorer
    return scorer


def _bertscore_f1_matrix(
    candidates: List[str],
    references: List[str],
) -> torch.Tensor:
    pair_count = len(candidates) * len(references)
    if pair_count == 0:
        return torch.empty((len(candidates), len(references)), dtype=torch.float32)

    scorer = _get_scorer(BERTSCORE_LANG, BERTSCORE_MODEL_TYPE)
    f1_rows: List[torch.Tensor] = []

    for candidate in candidates:
        repeated_candidates = [candidate] * len(references)
        try:
            score_output = scorer.score(repeated_candidates, references)
        except OverflowError:
            tokenizer = getattr(scorer, "_tokenizer", None)
            if tokenizer is None:
                raise
            tokenizer.model_max_length = TOKENIZER_MAX_LENGTH
            score_output = scorer.score(repeated_candidates, references)
        f1_scores = score_output[2]
        if isinstance(f1_scores, torch.Tensor):
            f1_rows.append(f1_scores.detach().cpu().to(dtype=torch.float32))
        else:
            f1_rows.append(torch.tensor(f1_scores, dtype=torch.float32))

    return torch.stack(f1_rows, dim=0)


def _soft_coverage(scores: torch.Tensor) -> torch.Tensor:
    clipped = scores.clamp(min=0.0, max=1.0 - 1e-7)
    not_covered = torch.exp(torch.log1p(-clipped).sum(dim=1))
    return 1.0 - not_covered


def _sentence_alignment(
    references: List[str],
    candidates: List[str],
) -> Tuple[float, float, float]:
    if not references or not candidates:
        return 0.0, 0.0, 0.0

    cand_to_ref = _bertscore_f1_matrix(candidates, references)
    ref_to_cand = _bertscore_f1_matrix(references, candidates)

    # how many candidate sentences are supported by references
    precision = float(_soft_coverage(cand_to_ref).mean().item())

    # how many reference sentences are covered by candidates
    recall = float(_soft_coverage(ref_to_cand).mean().item())

    f1 = safe_div(2 * precision * recall, precision + recall)

    return precision, recall, f1


class AlignmentEvaluator(Evaluator):
    def evaluate(
        self, review: CodeReviewEntry, pr: PullRequestEntry
    ) -> AlignmentResult:
        comment_refs = _collect_comment_references(pr)
        comment_cands = _collect_comment_candidates(review.content)

        interp_refs = _collect_interpretation_references(pr)
        interp_cands = _collect_interpretation_candidates(review.content)

        cp, cr, cf1 = _sentence_alignment(
            comment_refs,
            comment_cands,
        )
        ip, ir, if1 = _sentence_alignment(
            interp_refs,
            interp_cands,
        )

        return AlignmentResult(
            comment_precision=clamp01(cp),
            comment_recall=clamp01(cr),
            comment_f1=clamp01(cf1),
            interpretation_precision=clamp01(ip),
            interpretation_recall=clamp01(ir),
            interpretation_f1=clamp01(if1),
            comment_refs=comment_refs,
            comment_cands=comment_cands,
            interp_refs=interp_refs,
            interp_cands=interp_cands,
        )
