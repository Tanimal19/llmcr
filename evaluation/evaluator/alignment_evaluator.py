import torch
import re
import time
import requests
from functools import lru_cache
from scipy.optimize import linear_sum_assignment
from sentence_transformers import SentenceTransformer, util
from pathlib import Path
from typing import Any, List, Tuple
from dataclasses import dataclass, field
from dacite import from_dict
from . import Evaluator
from share.utils import (
    safe_div,
    clamp01,
    get_filename_from_pathstring,
    render_prompt_template,
)
from share.code_review_scheme import CodeReviewEntry, CodeReviewContent
from share.pull_request_scheme import PullRequestEntry

SBERT_MODEL_NAME = "google/embeddinggemma-300M"
SBERT_THRESHOLD = 0.5

SLM_MODEL_NAME = "nemotron-3-4b"
SLM_API_BASE_URL = "http://localhost:8080"
SLM_MAX_RETRIES = 3
PROMPT_TEMPLATE = (
    Path(__file__).with_name("alignment.prompt.txt").read_text(encoding="utf-8")
)


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


@lru_cache(maxsize=1)
def _get_sbert_model():
    model = SentenceTransformer(SBERT_MODEL_NAME)
    return model


def _hungarian_match(cost_matrix: torch.Tensor) -> List[Tuple[int, int]]:
    row_ind, col_ind = linear_sum_assignment(cost_matrix.cpu().numpy())
    return list(zip(row_ind, col_ind))


def _sbert_matching(
    references: List[str],
    candidates: List[str],
) -> List[Tuple[int, int, float]]:
    """Returns list of (ref_index, cand_index, similarity) for matched pairs."""

    if not references or not candidates:
        return []

    model = _get_sbert_model()

    ref_embeddings = model.encode(references, convert_to_tensor=True)
    cand_embeddings = model.encode(candidates, convert_to_tensor=True)
    similarity_matrix = util.cos_sim(ref_embeddings, cand_embeddings)

    matches = _hungarian_match(-similarity_matrix)
    valid_matches = [
        (r, c, similarity_matrix[r, c].item())
        for r, c in matches
        if similarity_matrix[r, c].item() > SBERT_THRESHOLD
    ]

    return valid_matches


@dataclass
class SlmAlignmentResponse:
    reason: str
    confidence: float
    is_match: str


def _slm_pair_matched(
    candidate: str,
    reference: str,
) -> bool:
    """Returns True if SLM judges candidate matches reference, False otherwise."""

    prompt = render_prompt_template(
        PROMPT_TEMPLATE,
        {
            "candidate": candidate,
            "reference": reference,
        },
    )

    payload = {
        "model": SLM_MODEL_NAME,
        "messages": [
            {"role": "user", "content": prompt},
        ],
        "temperature": 0,
        "max_tokens": 512,
    }
    headers = {
        "Content-Type": "application/json",
        "Authorization": f"Bearer dummy",
    }

    last_error: Any = None
    for attempt in range(1, SLM_MAX_RETRIES + 1):
        try:
            response = requests.post(
                SLM_API_BASE_URL + "/v1/chat/completions",
                json=payload,
                headers=headers,
                timeout=60,
            )
            response.raise_for_status()
            data = response.json()
            content = data.get("choices", [{}])[0].get("message", {}).get("content", "")
            alignment_response = from_dict(SlmAlignmentResponse, eval(content))

            print(f"SLM: {alignment_response}")

            if alignment_response.confidence > 0.5:
                return True if alignment_response.is_match.lower() == "true" else False
            else:
                return False  # low confidence treated as no match

        except Exception as exc:
            last_error = exc
            if attempt < SLM_MAX_RETRIES:
                time.sleep(0.5 * attempt)

    print(
        f"SLM pair classification failed after retries; fallback to no-match. error={last_error}"
    )
    return False


def _sentence_alignment(
    references: List[str],
    candidates: List[str],
) -> Tuple[float, float, float]:
    if not references or not candidates:
        return 0.0, 0.0, 0.0

    matches = _sbert_matching(references, candidates)
    precision = safe_div(len(matches), len(candidates))
    recall = safe_div(len(matches), len(references))
    f1 = safe_div(2 * precision * recall, precision + recall)
    print(f"SLM alignment: precision={precision:.4f}, recall={recall:.4f}, f1={f1:.4f}")

    return precision, recall, f1


class AlignmentEvaluator(Evaluator):
    def evaluate(
        self, review: CodeReviewEntry, pr: PullRequestEntry
    ) -> AlignmentResult:
        comment_refs = _collect_comment_references(pr)
        comment_cands = _collect_comment_candidates(review.content)
        cp, cr, cf1 = _sentence_alignment(comment_refs, comment_cands)

        interp_refs = _collect_interpretation_references(pr)
        interp_cands = _collect_interpretation_candidates(review.content)
        ip, ir, if1 = _sentence_alignment(interp_refs, interp_cands)

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
