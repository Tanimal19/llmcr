from difflib import SequenceMatcher
from typing import List

REPETITIVE_THRESHOLD = 0.9


def safe_div(numerator: float, denominator: float) -> float:
    if denominator == 0:
        return 0.0
    return numerator / denominator


def sentence_similarity(a: str, b: str) -> float:
    return SequenceMatcher(None, (a or "").lower(), (b or "").lower()).ratio()


def compute_repetitive_rate(
    sentences: List[str], threshold: float = REPETITIVE_THRESHOLD
) -> float:
    if not sentences:
        return 0.0

    clusters: List[List[str]] = []
    for sentence in sentences:
        placed = False
        for cluster in clusters:
            if sentence_similarity(sentence, cluster[0]) >= threshold:
                cluster.append(sentence)
                placed = True
                break
        if not placed:
            clusters.append([sentence])

    return 1.0 - safe_div(len(clusters), len(sentences))
