import torch
import re
from typing import Any, List, Tuple
from numbers import Integral
from functools import lru_cache
from dataclasses import dataclass, field
from scipy.optimize import linear_sum_assignment
from bert_score import BERTScorer
from transformers import AutoModelForSequenceClassification, AutoTokenizer
from sentence_transformers import SentenceTransformer, util
from . import Evaluator
from share.utils import (
    safe_div,
    clamp01,
    get_filename_from_pathstring,
)
from share.code_review_scheme import CodeReviewEntry, CodeReviewContent
from share.pull_request_scheme import PullRequestEntry

BERT_MODEL_NAME = "roberta-large"
SBERT_MODEL_NAME = "google/embeddinggemma-300M"
NLI_MODEL_NAME = "microsoft/deberta-xlarge-mnli"


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
def _get_bert_model() -> BERTScorer:
    device = "cuda" if torch.cuda.is_available() else "cpu"
    model = BERTScorer(
        lang="en",
        model_type=BERT_MODEL_NAME,
        device=device,
        rescale_with_baseline=True,
    )
    tokenizer = getattr(model, "_tokenizer", None)
    if tokenizer is not None:
        current_max_length = getattr(tokenizer, "model_max_length", None)
        if (
            not isinstance(current_max_length, Integral)
            or int(current_max_length) <= 0
            or int(current_max_length) > 100000
        ):
            tokenizer.model_max_length = 512

    return model


def _bertscore_f1_matrix(
    candidates: List[str],
    references: List[str],
) -> torch.Tensor:
    pair_count = len(candidates) * len(references)
    if pair_count == 0:
        return torch.empty((len(candidates), len(references)), dtype=torch.float32)

    scorer = _get_bert_model()
    f1_rows: List[torch.Tensor] = []

    for candidate in candidates:
        repeated_candidates = [candidate] * len(references)
        score_output = scorer.score(repeated_candidates, references)
        f1_scores = score_output[2]
        if isinstance(f1_scores, torch.Tensor):
            f1_rows.append(f1_scores.detach().cpu().to(dtype=torch.float32))
        else:
            f1_rows.append(torch.tensor(f1_scores, dtype=torch.float32))

    return torch.stack(f1_rows, dim=0)


def _max_coverage(scores):
    return scores.max(dim=1).values


def _soft_coverage(scores: torch.Tensor) -> torch.Tensor:
    clipped = scores.clamp(min=0.0, max=1.0 - 1e-7)
    not_covered = torch.exp(torch.log1p(-clipped).sum(dim=1))
    return 1.0 - not_covered


def _bert_sentence_alignment(
    references: List[str],
    candidates: List[str],
) -> Tuple[float, float, float]:
    if not references or not candidates:
        return 0.0, 0.0, 0.0

    cand_to_ref = _bertscore_f1_matrix(candidates, references)
    ref_to_cand = _bertscore_f1_matrix(references, candidates)

    precision = float(_max_coverage(cand_to_ref).mean().item())
    recall = float(_max_coverage(ref_to_cand).mean().item())
    f1 = safe_div(2 * precision * recall, precision + recall)
    print(
        f"BERTScore-based soft coverage: precision={precision:.4f}, recall={recall:.4f}, f1={f1:.4f}"
    )

    return precision, recall, f1


@lru_cache(maxsize=1)
def _get_sbert_model():
    model = SentenceTransformer(SBERT_MODEL_NAME)
    return model


def _hungarian_match(cost_matrix: torch.Tensor) -> List[Tuple[int, int]]:
    row_ind, col_ind = linear_sum_assignment(cost_matrix.cpu().numpy())
    return list(zip(row_ind, col_ind))


def _sbert_sentence_alignment(
    references: List[str],
    candidates: List[str],
) -> Tuple[float, float, float]:
    if not references or not candidates:
        return 0.0, 0.0, 0.0

    model = _get_sbert_model()

    ref_embeddings = model.encode(references, convert_to_tensor=True)
    cand_embeddings = model.encode(candidates, convert_to_tensor=True)
    similarity_matrix = util.cos_sim(ref_embeddings, cand_embeddings)

    matches = _hungarian_match(-similarity_matrix)
    valid_matches = [(r, c) for r, c in matches if similarity_matrix[r, c].item() > 0.5]
    for r, c in valid_matches:
        print(
            f"SBERT match:\n    candidate '{candidates[c]}'\n    reference '{references[r]}'\n    with similarity {similarity_matrix[r, c].item():.4f}"
        )

    precision = safe_div(len(valid_matches), len(candidates))
    recall = safe_div(len(valid_matches), len(references))
    f1 = safe_div(2 * precision * recall, precision + recall)
    print(
        f"SBERT alignment: precision={precision:.4f}, recall={recall:.4f}, f1={f1:.4f}"
    )

    return precision, recall, f1


@lru_cache(maxsize=1)
def _get_nli_model() -> Tuple[Any, Any]:
    tokenizer = AutoTokenizer.from_pretrained(NLI_MODEL_NAME)
    model = AutoModelForSequenceClassification.from_pretrained(NLI_MODEL_NAME)
    model.eval()
    return tokenizer, model


def _entailment_probability_matrix(
    premises: List[str],
    hypotheses: List[str],
) -> torch.Tensor:
    tokenizer, model = _get_nli_model()
    pair_count = len(premises) * len(hypotheses)
    if pair_count == 0:
        return torch.empty((len(premises), len(hypotheses)), dtype=torch.float32)

    id2label = model.config.id2label
    entailment_label_id = [i for i, l in id2label.items() if "entail" in l.lower()][0]

    batch_premises = [premise for premise in premises for _ in hypotheses]
    batch_hypotheses = [hypothesis for _ in premises for hypothesis in hypotheses]

    entailment_probs_batches: List[torch.Tensor] = []
    batch_size = 16
    with torch.no_grad():
        for idx in range(0, pair_count, batch_size):
            sub_premises = batch_premises[idx : idx + batch_size]
            sub_hypotheses = batch_hypotheses[idx : idx + batch_size]
            inputs = tokenizer(
                sub_premises,
                sub_hypotheses,
                return_tensors="pt",
                padding=True,
                truncation=True,
                max_length=512,
            )
            logits = model(**inputs).logits
            probs = torch.softmax(logits, dim=-1)
            entailment_probability = probs[:, entailment_label_id]
            entailment_probs_batches.append(entailment_probability)

    entailment_probs = torch.cat(entailment_probs_batches, dim=0)
    return entailment_probs.reshape(len(premises), len(hypotheses))


def _nli_sentence_alignment(
    references: List[str],
    candidates: List[str],
) -> Tuple[float, float, float]:
    if not references or not candidates:
        return 0.0, 0.0, 0.0

    nli_probs = _entailment_probability_matrix(references, candidates)
    matches = _hungarian_match(-nli_probs)
    valid_matches = [(r, c) for r, c in matches if nli_probs[r, c].item() > 0.5]
    for r, c in valid_matches:
        print(
            f"NLI match:\n    candidate '{candidates[c]}'\n    reference '{references[r]}'\n    with prob {nli_probs[r, c].item():.4f}"
        )

    precision = safe_div(len(valid_matches), len(candidates))
    recall = safe_div(len(valid_matches), len(references))
    f1 = safe_div(2 * precision * recall, precision + recall)
    print(f"NLI alignment: precision={precision:.4f}, recall={recall:.4f}, f1={f1:.4f}")

    return precision, recall, f1


class AlignmentEvaluator(Evaluator):
    def evaluate(
        self, review: CodeReviewEntry, pr: PullRequestEntry
    ) -> AlignmentResult:
        comment_refs = _collect_comment_references(pr)
        comment_cands = _collect_comment_candidates(review.content)
        print(
            f"Evaluating comment alignment: {len(comment_refs)} references, {len(comment_cands)} candidates"
        )
        cp_bert, cr_bert, _ = _bert_sentence_alignment(
            comment_refs,
            comment_cands,
        )
        cp_sbert, cr_sbert, _ = _sbert_sentence_alignment(
            comment_refs,
            comment_cands,
        )
        cp_nli, cr_nli, _ = _nli_sentence_alignment(
            comment_refs,
            comment_cands,
        )
        cp = (cp_bert + cp_sbert + cp_nli) / 3
        cr = (cr_bert + cr_sbert + cr_nli) / 3
        cf1 = safe_div(2 * cp * cr, cp + cr)
        print(
            f"Combined comment alignment: precision={cp:.4f}, recall={cr:.4f}, f1={cf1:.4f}"
        )

        interp_refs = _collect_interpretation_references(pr)
        interp_cands = _collect_interpretation_candidates(review.content)
        print(
            f"Evaluating interpretation alignment: {len(interp_refs)} references, {len(interp_cands)} candidates"
        )
        ip_bert, ir_bert, _ = _bert_sentence_alignment(
            interp_refs,
            interp_cands,
        )
        ip_sbert, ir_sbert, _ = _sbert_sentence_alignment(
            interp_refs,
            interp_cands,
        )
        ip_nli, ir_nli, _ = _nli_sentence_alignment(
            interp_refs,
            interp_cands,
        )
        ip = (ip_bert + ip_sbert + ip_nli) / 3
        ir = (ir_bert + ir_sbert + ir_nli) / 3
        if1 = safe_div(2 * ip * ir, ip + ir)
        print(
            f"Combined interpretation alignment: precision={ip:.4f}, recall={ir:.4f}, f1={if1:.4f}"
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
