import faiss
import os
import numpy as np
from typing import List


INDEX = None
CURRENT_INDEX_NAME = None


def get_index_path(index_name: str) -> str:
    return f"./app/data/{index_name}.index"


def load_index(index_name: str) -> str:
    global INDEX, CURRENT_INDEX_NAME

    if not os.path.exists(get_index_path(index_name)):
        return f"Index file {index_name} does not exist."
    else:
        INDEX = faiss.read_index(get_index_path(index_name))
        CURRENT_INDEX_NAME = index_name
        return f"Load index {index_name} from file, index.ntotal = {INDEX.ntotal}"


def remove_index(index_name: str) -> str:
    global INDEX, CURRENT_INDEX_NAME
    if os.path.exists(get_index_path(index_name)):
        os.remove(get_index_path(index_name))
        INDEX = None
        CURRENT_INDEX_NAME = None
        return f"Index {index_name} removed."
    else:
        return f"Index {index_name} does not exist."


def create_index(index_name: str, dim: int) -> str:
    base = faiss.IndexFlatIP(dim)
    idmap = faiss.IndexIDMap2(base)
    faiss.write_index(idmap, get_index_path(index_name))
    return f"Created new index {index_name} with dimension {dim}."


def get_index(index_name: str, dim: int | None):
    global INDEX, CURRENT_INDEX_NAME

    if CURRENT_INDEX_NAME != index_name:
        load_index(index_name)

    if INDEX is None:
        if dim is None:
            print("Dimension must be provided to create a new index.")
            return None
        create_index(index_name, dim)
        load_index(index_name)

    return INDEX


def add_index(index_name: str, ids: List[int], vectors: List[List[float]]) -> None:
    index = get_index(index_name, len(vectors[0]))
    if index is None:
        print("Failed to get index.")
        return

    vectors_np = np.array(vectors, dtype=np.float32)
    ids_np = np.array(ids, dtype=np.int64)
    faiss.normalize_L2(vectors_np)

    index.add_with_ids(vectors_np, ids_np)
    faiss.write_index(index, get_index_path(index_name))
    print(f"Add {len(ids)} index to {index_name}.")

    load_index(index_name)


def search(index_name: str, query_vector: List[float], top_k: int):
    index = get_index(index_name, None)
    if index is None:
        print("Failed to get index.")
        return [], []

    query_vector_np = np.array([query_vector], dtype=np.float32)
    faiss.normalize_L2(query_vector_np)

    scores, ids = index.search(query_vector_np, top_k)
    return scores[0].tolist(), ids[0].tolist()
