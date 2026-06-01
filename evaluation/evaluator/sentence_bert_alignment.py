from typing import List, Tuple
from sentence_transformers import SentenceTransformer, util

_MODEL_CACHE: dict[str, SentenceTransformer] = {}


def is_sentence_bert_available() -> bool:
    return SentenceTransformer is not None and util is not None


def _safe_div(numerator: float, denominator: float) -> float:
    if denominator == 0:
        return 0.0
    return numerator / denominator


def _get_model(model_name: str):
    if not is_sentence_bert_available():
        return None

    cached = _MODEL_CACHE.get(model_name)
    if cached is not None:
        return cached

    model = SentenceTransformer(model_name)
    _MODEL_CACHE[model_name] = model
    return model


def sentence_bert_sentence_alignment(
    references: List[str],
    candidates: List[str],
    model_name: str = "google/embeddinggemma-300m",
) -> Tuple[float, float, float]:
    if not references and not candidates:
        return 0.0, 0.0, 0.0
    if not references or not candidates:
        return 0.0, 0.0, 0.0

    model = _get_model(model_name)
    if model is None or util is None:
        return 0.0, 0.0, 0.0

    ref_embeddings = model.encode(references, convert_to_tensor=True)
    cand_embeddings = model.encode(candidates, convert_to_tensor=True)

    similarity_matrix = util.cos_sim(cand_embeddings, ref_embeddings)

    precision = float(similarity_matrix.max(dim=1).values.mean().item())
    recall = float(similarity_matrix.max(dim=0).values.mean().item())
    f1 = _safe_div(2 * precision * recall, precision + recall)

    return precision, recall, f1
