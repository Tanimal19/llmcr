from typing import Any, Dict, List, Optional, Tuple
import torch
from transformers import AutoModelForSequenceClassification, AutoTokenizer

from utils import split_sentences, to_string_list, safe_div, clamp01
from scheme import ParsedReview

NLI_MODEL_NAME = "microsoft/deberta-xlarge-mnli"
_MODEL_CACHE: dict[str, Tuple[Any, Any, int]] = {}


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


def _get_model(model_name: str) -> Tuple[Any, Any, int]:
    cached = _MODEL_CACHE.get(model_name)
    if cached is not None:
        return cached

    tokenizer = AutoTokenizer.from_pretrained(model_name)
    model = AutoModelForSequenceClassification.from_pretrained(model_name)
    model.eval()

    entailment_id = next(
        (
            idx
            for idx, label in model.config.id2label.items()
            if label.lower().startswith("entail")
        ),
        -1,
    )
    if entailment_id < 0:
        raise ValueError(f"Cannot find entailment label for model: {model_name}")

    _MODEL_CACHE[model_name] = (tokenizer, model, entailment_id)
    return tokenizer, model, entailment_id


def _entailment_probability_matrix(
    premises: List[str],
    hypotheses: List[str],
) -> torch.Tensor:
    tokenizer, model, entailment_id = _get_model(NLI_MODEL_NAME)
    pair_count = len(premises) * len(hypotheses)
    if pair_count == 0:
        return torch.empty((len(premises), len(hypotheses)), dtype=torch.float32)

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
                max_length=1024,
            )
            logits = model(**inputs).logits
            probs = torch.softmax(logits, dim=-1)
            entailment_probs_batches.append(probs[:, entailment_id])

    entailment_probs = torch.cat(entailment_probs_batches, dim=0)
    return entailment_probs.reshape(len(premises), len(hypotheses))


def nli_sentence_alignment(
    references: List[str],
    candidates: List[str],
) -> Tuple[float, float, float]:
    if not references and not candidates:
        return 0.0, 0.0, 0.0
    if not references or not candidates:
        return 0.0, 0.0, 0.0

    cand_to_ref = _entailment_probability_matrix(candidates, references)
    ref_to_cand = _entailment_probability_matrix(references, candidates)

    precision = float(cand_to_ref.max(dim=1).values.mean().item())
    recall = float(ref_to_cand.max(dim=1).values.mean().item())
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
