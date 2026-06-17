import faiss
import os
import numpy as np
from typing import List, Optional

DATA_DIR = "./app/data"
DATA_DIR_ABS = os.path.abspath(DATA_DIR)
INDEX_PATH = os.path.join(DATA_DIR_ABS, "main.index")


def load_index() -> faiss.Index | None:
    if not os.path.exists(INDEX_PATH):
        return None
    return faiss.read_index(INDEX_PATH)


def get_or_create_index(dim: int) -> faiss.Index:
    index = load_index()
    if index is not None:
        return index
    os.makedirs(DATA_DIR_ABS, exist_ok=True)
    base = faiss.IndexFlatIP(dim)
    index = faiss.IndexIDMap2(base)
    faiss.write_index(index, INDEX_PATH)
    return index


def add_vectors(ids: List[int], vectors: List[List[float]]) -> None:
    if not vectors:
        return
    index = get_or_create_index(len(vectors[0]))
    vectors_np = np.asarray(vectors, dtype=np.float32)
    ids_np = np.asarray(ids, dtype=np.int64)
    faiss.normalize_L2(vectors_np)
    index.add_with_ids(vectors_np, ids_np)
    faiss.write_index(index, INDEX_PATH)


def search(
    query_vector: List[float],
    top_k: int,
    allowed_ids: Optional[List[int]] = None,
) -> tuple[list, list]:
    index = load_index()
    if index is None:
        return [], []

    query_np = np.asarray([query_vector], dtype=np.float32)
    faiss.normalize_L2(query_np)

    if allowed_ids is not None:
        sel = faiss.IDSelectorBatch(np.asarray(allowed_ids, dtype=np.int64))
        params = faiss.SearchParameters(sel=sel)
        scores, ids = index.search(query_np, top_k, params=params)
    else:
        scores, ids = index.search(query_np, top_k)

    return scores[0].tolist(), ids[0].tolist()


def remove_vectors(ids: List[int]) -> int:
    if not ids:
        return 0
    index = load_index()
    if index is None:
        return 0
    ids_np = np.asarray(ids, dtype=np.int64)
    removed = index.remove_ids(ids_np)
    faiss.write_index(index, INDEX_PATH)
    return removed
