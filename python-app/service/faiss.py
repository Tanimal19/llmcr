import faiss
import numpy as np
from typing import List
from service.database import Chunk
from config import Config


class FaissService:
    def __init__(self) -> None:
        self.index_path = Config.FAISS_INDEX_PATH
        self.index = self.load_index()
        print(f"Loaded FAISS index with {self.index.ntotal} vectors")

    @staticmethod
    def create_index(chunks: List[Chunk]):
        print(f"Creating FAISS index...")

        embeddings = np.array(
            [chunk.embedding for chunk in chunks if chunk.embedding is not None],
            dtype=np.float32,
        )
        ids = np.array(
            [chunk.id for chunk in chunks if chunk.id is not None],
            dtype=np.int64,
        )

        # Create FAISS index (using L2 distance)
        index = faiss.IndexFlatL2(embeddings.shape[1])

        # Create ID mapping
        id_index = faiss.IndexIDMap2(index)

        # Add vectors to the index
        id_index.add_with_ids(embeddings, ids)

        # Save index to file
        faiss.write_index(index, Config.FAISS_INDEX_PATH)

        print(f"Created FAISS index with {id_index.ntotal} vectors")
        print(f"Saved FAISS index to {Config.FAISS_INDEX_PATH}")

    @staticmethod
    def load_index():
        print(f"Loading FAISS index from {Config.FAISS_INDEX_PATH}...")
        return faiss.read_index(Config.FAISS_INDEX_PATH)

    def search(self, query_embedding: np.ndarray, top_k: int):
        if self.index is None:
            return None, None

        query_embedding = np.array([query_embedding], dtype=np.float32)
        distances, indices = self.index.search(query_embedding, top_k)
        return distances, indices
