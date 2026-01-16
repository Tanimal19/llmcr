import faiss
import os
import numpy as np
from typing import List


index = None
index_path = "./app/data/index.bin"


def load_index(update=False) -> str:
    global index

    if not os.path.exists(index_path):
        index = None
        return "No index file found."

    if index is not None and not update:
        return f"Index already loaded, index.ntotal = {index.ntotal}"

    index = faiss.read_index(index_path)
    return f"Load index from file, index.ntotal = {index.ntotal}"


def remove_index() -> str:
    global index
    if os.path.exists(index_path):
        os.remove(index_path)
        index = None
        return "Index file removed."
    else:
        return "No index file to remove."


def create_index(dim) -> None:
    print(f"Creating FAISS index with dimension: {dim}")

    base = faiss.IndexFlatIP(dim)
    index = faiss.IndexIDMap2(base)
    faiss.write_index(index, index_path)


def add_index(ids: List[int], vectors: List[List[float]]) -> None:
    global index
    if index is None:
        create_index(len(vectors[0]))
        load_index()

    if index is None:
        print("Failed to load index.")
        return

    vectors_np = np.array(vectors, dtype=np.float32)
    ids_np = np.array(ids, dtype=np.int64)
    faiss.normalize_L2(vectors_np)

    index.add_with_ids(vectors_np, ids_np)
    faiss.write_index(index, index_path)

    load_index(update=True)
    print(f"add {len(ids)} index, index.ntotal = {index.ntotal}")


def search(query_vector: List[float], top_k: int):
    global index
    if index is None:
        load_index()

    if index is None:
        print("Failed to load index.")
        return [], []

    query_vector_np = np.array([query_vector], dtype=np.float32)
    faiss.normalize_L2(query_vector_np)

    scores, ids = index.search(query_vector_np, top_k)
    return scores[0].tolist(), ids[0].tolist()
