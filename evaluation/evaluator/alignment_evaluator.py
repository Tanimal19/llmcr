from typing import List, Tuple
from utils import split_sentences, to_string_list, safe_div, clamp01
from typing import Any, Dict, List, Optional, Tuple
from scheme import ParsedReview
from bert_score import score as bert_score

BERT_SCORE_MODEL_TYPE = "microsoft/deberta-xlarge-mnli"


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


def bert_score_alignment(
    references: List[str],
    candidates: List[str],
) -> Tuple[float, float, float]:
    if not references and not candidates:
        return 0.0, 0.0, 0.0
    if not references or not candidates:
        return 0.0, 0.0, 0.0

    pair_count = max(len(references), len(candidates))
    padded_refs = (references + [""] * pair_count)[:pair_count]
    padded_cands = (candidates + [""] * pair_count)[:pair_count]

    precision_scores, recall_scores, f1_scores = bert_score(
        cands=padded_cands,
        refs=padded_refs,
        model_type=BERT_SCORE_MODEL_TYPE,
        lang="en",
        rescale_with_baseline=True,
        verbose=False,
    )

    precision = float(precision_scores.mean().item())
    recall = float(recall_scores.mean().item())
    f1 = float(f1_scores.mean().item())

    return precision, recall, f1


def review_alignment(
    parsed: ParsedReview,
    pr_entry: Optional[Dict[str, Any]],
) -> Dict[str, Any]:
    comment_refs = collect_comment_references(pr_entry)
    comment_cands = collect_comment_candidates(parsed)

    interp_refs = collect_interpretation_references(pr_entry)
    interp_cands = collect_interpretation_candidates(parsed)

    cp, cr, cf1 = bert_score_alignment(
        comment_refs,
        comment_cands,
    )
    ip, ir, if1 = bert_score_alignment(
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
