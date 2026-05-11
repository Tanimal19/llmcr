import faiss
import os
import numpy as np
from typing import List

DATA_DIR = "./app/data"


def get_index_path(index_name: str) -> str:
    return os.path.join(DATA_DIR, f"{index_name}.index")


def create_index(index_name: str, dim: int) -> faiss.Index:
    base = faiss.IndexFlatIP(dim)
    index = faiss.IndexIDMap2(base)
    faiss.write_index(index, get_index_path(index_name))
    return index


def load_index(index_name: str) -> faiss.Index | None:
    path = get_index_path(index_name)
    if not os.path.exists(path):
        return None
    return faiss.read_index(path)


def get_or_create_index(index_name: str, dim: int | None) -> faiss.Index | None:
    index = load_index(index_name)

    if index is not None:
        return index

    if dim is None:
        print("Dimension must be provided to create a new index.")
        return None

    return create_index(index_name, dim)


def add_index_ids(
    index_name: str,
    ids: List[int],
    vectors: List[List[float]],
) -> None:
    if not vectors:
        print("Vectors is empty.")
        return

    dim = len(vectors[0])
    index = get_or_create_index(index_name, dim)

    if index is None:
        print("Failed to get index.")
        return

    vectors_np = np.asarray(vectors, dtype=np.float32)
    ids_np = np.asarray(ids, dtype=np.int64)

    faiss.normalize_L2(vectors_np)
    index.add_with_ids(vectors_np, ids_np)

    faiss.write_index(index, get_index_path(index_name))
    print(f"Add {len(ids)} vectors to index '{index_name}', ntotal={index.ntotal}")


def search(
    index_name: str,
    query_vector: List[float],
    top_k: int,
):
    index = load_index(index_name)
    if index is None:
        print(f"Index '{index_name}' does not exist.")
        return [], []

    query_np = np.asarray([query_vector], dtype=np.float32)
    faiss.normalize_L2(query_np)

    scores, ids = index.search(query_np, top_k)
    return scores[0].tolist(), ids[0].tolist()


def remove_index_ids(index_name: str, ids: List[int]) -> None:
    if not ids:
        print("Ids is empty.")
        return

    index = load_index(index_name)
    if index is None:
        print(f"Index '{index_name}' does not exist.")
        return

    ids_np = np.asarray(ids, dtype=np.int64)
    removed = index.remove_ids(ids_np)

    faiss.write_index(index, get_index_path(index_name))
    print(f"Removed {removed} vectors from index '{index_name}', ntotal={index.ntotal}")


def remove_index(index_name: str) -> None:
    path = get_index_path(index_name)
    if os.path.exists(path):
        os.remove(path)
        print(f"Index '{index_name}' removed.")
    else:
        print(f"Index '{index_name}' does not exist.")
