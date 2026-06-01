from typing import Any, Dict, List, Optional, Tuple
import torch
from transformers import AutoModelForSequenceClassification, AutoTokenizer

from utils import split_sentences, to_string_list, safe_div, clamp01
from scheme import ParsedReview

NLI_MODEL_NAME = "microsoft/deberta-xlarge-mnli"
_MODEL_CACHE: dict[str, Tuple[Any, Any]] = {}
TOP_K = 3


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


def _get_model(model_name: str) -> Tuple[Any, Any]:
    cached = _MODEL_CACHE.get(model_name)
    if cached is not None:
        return cached

    tokenizer = AutoTokenizer.from_pretrained(model_name)
    model = AutoModelForSequenceClassification.from_pretrained(model_name)
    model.eval()

    _MODEL_CACHE[model_name] = (tokenizer, model)
    return tokenizer, model


def _entailment_probability_matrix(
    premises: List[str],
    hypotheses: List[str],
) -> torch.Tensor:
    tokenizer, model = _get_model(NLI_MODEL_NAME)
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
            entailment_score = entailment_probability
            entailment_probs_batches.append(entailment_score)

    entailment_probs = torch.cat(entailment_probs_batches, dim=0)
    return entailment_probs.reshape(len(premises), len(hypotheses))


def _soft_coverage(scores: torch.Tensor) -> torch.Tensor:
    clipped = scores.clamp(min=0.0, max=1.0 - 1e-7)
    not_covered = torch.exp(torch.log1p(-clipped).sum(dim=1))
    return 1.0 - not_covered


def nli_sentence_alignment(
    references: List[str],
    candidates: List[str],
) -> Tuple[float, float, float]:
    if not references and not candidates:
        return 0.0, 0.0, 0.0
    if not references or not candidates:
        return 0.0, 0.0, 0.0

    cand_to_ref = _entailment_probability_matrix(candidates, references)
    print(f"Entailment probability matrix (candidates to references):\n{cand_to_ref}")

    ref_to_cand = _entailment_probability_matrix(references, candidates)
    print(f"Entailment probability matrix (references to candidates):\n{ref_to_cand}")

    # how many candidate sentences are supported by references
    precision = float(_soft_coverage(cand_to_ref).mean().item())
    # how many reference sentences are covered by candidates
    recall = float(_soft_coverage(ref_to_cand).mean().item())
    f1 = safe_div(2 * precision * recall, precision + recall)

    return precision, recall, f1


def review_alignment(
    parsed: ParsedReview,
    pr_entry: Optional[Dict[str, Any]],
) -> Dict[str, Any]:
    comment_refs = collect_comment_references(pr_entry)
    comment_cands = collect_comment_candidates(parsed)

    interp_refs = collect_interpretation_references(pr_entry)
    interp_cands = collect_interpretation_candidates(parsed)

    cp, cr, cf1 = nli_sentence_alignment(
        comment_refs,
        comment_cands,
    )
    ip, ir, if1 = nli_sentence_alignment(
        interp_refs,
        interp_cands,
    )

    return {
        "comment_precision": clamp01(cp),
        "comment_recall": clamp01(cr),
        "comment_f1": clamp01(cf1),
        "interpretation_precision": clamp01(ip),
        "interpretation_recall": clamp01(ir),
        "interpretation_f1": clamp01(if1),
        "comment_refs": comment_refs,
        "comment_cands": comment_cands,
        "interp_refs": interp_refs,
        "interp_cands": interp_cands,
    }
