import faiss
import numpy as np
import os
import pickle
from typing import List, Tuple
from config import Config


class FAISSSearchService:
    def __init__(self):
        self.embedding_dim = Config.EMBEDDING_DIM
        self.index = None
        self.doc_ids = []
        self.index_path = Config.FAISS_INDEX_PATH

        # Try to load existing index
        if os.path.exists(f"{self.index_path}"):
            self.load_index()
        else:
            self._create_index()

    def _create_index(self):
        # Using IndexFlatL2 for exact search (L2 distance)
        # For larger datasets, consider IndexIVFFlat or IndexHNSWFlat
        self.index = faiss.IndexFlatL2(self.embedding_dim)
        print(f"Created new FAISS index with dimension {self.embedding_dim}")

    def add_vectors(self, embeddings: np.ndarray, doc_ids: List[int]):
        if self.index is None:
            self._create_index()

        # Ensure embeddings are float32 (required by FAISS)
        embeddings = embeddings.astype("float32")

        # Add to index
        self.index.add(embeddings)
        self.doc_ids.extend(doc_ids)

        print(
            f"Added {len(doc_ids)} vectors to FAISS index. Total: {self.index.ntotal}"
        )

    def search(
        self, query_embedding: np.ndarray, k: int = 5
    ) -> Tuple[List[int], List[float]]:
        if self.index is None or self.index.ntotal == 0:
            return [], []

        # Ensure query is 2D array of float32
        query_embedding = query_embedding.astype("float32").reshape(1, -1)

        # Limit k to available documents
        k = min(k, self.index.ntotal)

        # Search
        distances, indices = self.index.search(query_embedding, k)

        # Convert indices to document IDs
        result_doc_ids = [self.doc_ids[idx] for idx in indices[0]]
        result_distances = distances[0].tolist()

        return result_doc_ids, result_distances

    def rebuild_index(self, embeddings: np.ndarray, doc_ids: List[int]):
        self._create_index()
        self.doc_ids = []

        if len(embeddings) > 0:
            self.add_vectors(embeddings, doc_ids)

    def save_index(self):
        os.makedirs(os.path.dirname(self.index_path) or ".", exist_ok=True)

        if self.index is not None:
            # Save FAISS index
            faiss.write_index(self.index, f"{self.index_path}.index")

            # Save doc_ids mapping
            with open(f"{self.index_path}.pkl", "wb") as f:
                pickle.dump(self.doc_ids, f)

            print(f"FAISS index saved to {self.index_path}")

    def load_index(self):
        try:
            # Load FAISS index
            self.index = faiss.read_index(f"{self.index_path}.index")

            # Load doc_ids mapping
            with open(f"{self.index_path}.pkl", "rb") as f:
                self.doc_ids = pickle.load(f)

            print(
                f"Loaded FAISS index from {self.index_path} with {self.index.ntotal} vectors"
            )
        except Exception as e:
            print(f"Failed to load index: {e}")
            self._create_index()

    def clear(self):
        self._create_index()
        self.doc_ids = []
        print("FAISS index cleared")

    def get_index_size(self) -> int:
        return self.index.ntotal if self.index else 0
