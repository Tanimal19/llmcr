import faiss
import numpy as np
from typing import List


index = None
index_path = "faiss_index.index"


def is_index_loaded() -> bool:
    global index
    return index is not None


def load_index():
    global index
    print(f"load index from {index_path}")
    index = faiss.read_index(index_path)
    print(f"loaded {index.ntotal} vectors")


def create_index(ids: List[int], vectors: List[List[float]]) -> None:
    print(f"Creating FAISS index...")

    vectors_np = np.array(vectors, dtype=np.float32)
    ids_np = np.array(ids, dtype=np.int64)
    faiss.normalize_L2(vectors_np)
    index = faiss.IndexFlatIP(vectors_np.shape[1])

    id_index = faiss.IndexIDMap2(index)
    id_index.add_with_ids(vectors_np, ids_np)

    faiss.write_index(id_index, index_path)

    print(f"create {id_index.ntotal} vectors")
    print(f"add index to {index_path}")

    # reload the index
    load_index()


def search(query_vector: List[float], top_k: int):
    global index
    if index is None:
        return None, None

    query_vector_np = np.array([query_vector], dtype=np.float32)
    scores, ids = index.search(query_vector_np, top_k)
    return scores, ids
