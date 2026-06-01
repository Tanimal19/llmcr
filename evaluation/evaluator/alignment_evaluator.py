from typing import List, Tuple
from utils import split_sentences, to_string_list, safe_div, clamp01
from typing import Any, Dict, List, Optional, Tuple
from scheme import ParsedReview
from sentence_transformers import SentenceTransformer, util

SBERT_MODEL_NAME = "all-MiniLM-L6-v2"
_MODEL_CACHE: dict[str, SentenceTransformer] = {}


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


def _get_model(model_name: str):
    cached = _MODEL_CACHE.get(model_name)
    if cached is not None:
        return cached

    model = SentenceTransformer(model_name)
    _MODEL_CACHE[model_name] = model
    return model


def sentence_bert_sentence_alignment(
    references: List[str],
    candidates: List[str],
) -> Tuple[float, float, float]:
    if not references and not candidates:
        return 0.0, 0.0, 0.0
    if not references or not candidates:
        return 0.0, 0.0, 0.0

    model = _get_model(SBERT_MODEL_NAME)
    if model is None or util is None:
        return 0.0, 0.0, 0.0

    ref_embeddings = model.encode(references, convert_to_tensor=True)
    cand_embeddings = model.encode(candidates, convert_to_tensor=True)

    similarity_matrix = util.cos_sim(cand_embeddings, ref_embeddings)

    precision = float(similarity_matrix.max(dim=1).values.mean().item())
    recall = float(similarity_matrix.max(dim=0).values.mean().item())
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

    cp, cr, cf1 = sentence_bert_sentence_alignment(
        comment_refs,
        comment_cands,
    )
    ip, ir, if1 = sentence_bert_sentence_alignment(
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
