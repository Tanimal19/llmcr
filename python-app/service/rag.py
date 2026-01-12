import numpy as np
from typing import List
from service.embedding import EmbeddingService
from service.database import DatabaseService
from service.faiss import FaissService
from service.chatbot import ChatbotService


class RAGService:
    def __init__(
        self,
        embedding_service: EmbeddingService,
        database_service: DatabaseService,
        faiss_service: FaissService,
    ):
        self.embedding_service = embedding_service
        self.database_service = database_service
        self.faiss_service = faiss_service

    def retrieve(self, query: str, top_k: int):
        print(f"Retrieving relevant chunks for query: {query}")

        # Step 1: Generate embedding for the query
        query_embedding = self.embedding_service.model.encode_document(query)
        query_embedding = np.array(query_embedding, dtype=np.float32)

        # Step 2: Search FAISS index for similar chunks
        distances, indices = self.faiss_service.search(query_embedding, top_k)

        print(distances)
        print(indices)

        # Step 3: Retrieve chunk content from database
        chunks = []
        if indices is not None:
            for idx in indices[0]:
                chunk = self.database_service.get_chunk_content_by_id(idx)
                if chunk:
                    chunks.append(chunk)

        return chunks
