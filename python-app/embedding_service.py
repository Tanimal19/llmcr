import numpy as np
from typing import List, Union
from config import Config
from llama_cpp import Llama


class EmbeddingService:
    def __init__(self):
        self.model_path = Config.EMBEDDING_MODEL_PATH

        # Initialize llama-cpp with embedding mode
        self.llm = Llama(
            model_path=self.model_path,
            embedding=True,  # Enable embedding mode
            n_ctx=2048,  # Context window size
            n_batch=512,  # Batch size for processing
            verbose=False,
        )

    def encode(self, text: str) -> np.ndarray:
        """
        Encode a single text into an embedding vector.

        Args:
            text: The text to encode

        Returns:
            numpy array of the embedding
        """
        if not text or not text.strip():
            raise ValueError("Text cannot be empty")

        embedding = self.llm.embed(text)
        return np.array(embedding, dtype=np.float32)

    def encode_batch(self, texts: List[str]) -> np.ndarray:
        """
        Encode multiple texts into embedding vectors.

        Args:
            texts: List of texts to encode

        Returns:
            numpy array of shape (n_texts, embedding_dim)
        """
        if not texts:
            raise ValueError("Texts list cannot be empty")

        embeddings = []
        for text in texts:
            if text and text.strip():
                embedding = self.llm.embed(text)
                embeddings.append(embedding)
            else:
                # Handle empty texts with zero vector
                embedding_dim = len(self.llm.embed("dummy"))
                embeddings.append([0.0] * embedding_dim)

        return np.array(embeddings, dtype=np.float32)
