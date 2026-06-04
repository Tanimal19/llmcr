from typing import Any, List, Tuple
import torch
import re
from functools import lru_cache
from dataclasses import dataclass, field
from transformers import AutoModelForSequenceClassification, AutoTokenizer
from . import Evaluator
from .entity_extractor import extract_entities
from share.utils import safe_div, clamp01, get_filename_from_pathstring
from share.code_review_scheme import CodeReviewEntry, CodeReviewContent
from share.pull_request_scheme import PullRequestEntry

NLI_MODEL_NAME = "microsoft/deberta-xlarge-mnli"
JACCARD_WEIGHT = 0.5

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
            print(f"Processing NLI batch {idx} to {min(idx + batch_size, pair_count)} / {pair_count}...")
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
            entailment_score = entailment_probability
            entailment_probs_batches.append(entailment_score)

    entailment_probs = torch.cat(entailment_probs_batches, dim=0)
    return entailment_probs.reshape(len(premises), len(hypotheses))


def _soft_coverage(scores: torch.Tensor) -> torch.Tensor:
    clipped = scores.clamp(min=0.0, max=1.0 - 1e-7)
    not_covered = torch.exp(torch.log1p(-clipped).sum(dim=1))
    return 1.0 - not_covered

def _max_coverage(scores: torch.Tensor) -> torch.Tensor:
    return scores.max(dim=1).values


def _jaccard_matrix(
    premises: List[str],
    hypotheses: List[str],
) -> torch.Tensor:
    premise_entities = [extract_entities(p) for p in premises]
    hypothesis_entities = [extract_entities(h) for h in hypotheses]

    matrix = torch.zeros(len(premises), len(hypotheses), dtype=torch.float32)
    for i, pe in enumerate(premise_entities):
        for j, he in enumerate(hypothesis_entities):
            union = pe | he
            matrix[i, j] = len(pe & he) / len(union) if union else 0.0
    return matrix


def _sentence_alignment(
    references: List[str],
    candidates: List[str],
) -> Tuple[float, float, float]:
    if not references or not candidates:
        return 0.0, 0.0, 0.0

    cand_to_ref_nli = _entailment_probability_matrix(candidates, references)
    ref_to_cand_nli = _entailment_probability_matrix(references, candidates)

    cand_to_ref_jaccard = _jaccard_matrix(candidates, references)
    ref_to_cand_jaccard = _jaccard_matrix(references, candidates)

    cand_to_ref = cand_to_ref_jaccard * JACCARD_WEIGHT + cand_to_ref_nli * (1 - JACCARD_WEIGHT)
    ref_to_cand = ref_to_cand_jaccard * JACCARD_WEIGHT + ref_to_cand_nli * (1 - JACCARD_WEIGHT)

    precision = float(_max_coverage(cand_to_ref).mean().item())
    recall = float(_max_coverage(ref_to_cand).mean().item())
    f1 = safe_div(2 * precision * recall, precision + recall)

    return precision, recall, f1


class AlignmentEvaluator(Evaluator):
    def evaluate(
        self, review: CodeReviewEntry, pr: PullRequestEntry
    ) -> AlignmentResult:
        comment_refs = _collect_comment_references(pr)
        comment_cands = _collect_comment_candidates(review.content)
        print(f"Evaluating comment alignment: {len(comment_refs)} references, {len(comment_cands)} candidates")
        cp, cr, cf1 = _sentence_alignment(
            comment_refs,
            comment_cands,
        )

        interp_refs = _collect_interpretation_references(pr)
        interp_cands = _collect_interpretation_candidates(review.content)
        print(f"Evaluating interpretation alignment: {len(interp_refs)} references, {len(interp_cands)} candidates")
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
