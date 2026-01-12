from config import Config
from typing import List
import time
from service.embedding import EmbeddingService
from service.database import Chunk, Document, DatabaseService
from service.faiss import FaissUtils


class LoadService:
    def __init__(self, chunk_size: int = 500, overlap_size: int = 50):
        self.chunk_size = chunk_size
        self.overlap_size = overlap_size
        self.embedding_service = EmbeddingService()
        self.database_service = DatabaseService()

    def run(self):
        start_time = time.time()
        print("\n=== Starting document processing pipeline ===")

        # Step 1: Load documents
        step_start = time.time()
        documents = self.database_service.load_documents()
        print(f"Loaded documents: {time.time() - step_start}s")

        # Step 2: Chunk documents
        step_start = time.time()
        chunks = self.embedding_service.generate_chunks(documents)
        print(f"Chunked documents: {time.time() - step_start}s")

        # Step 3: Save chunks
        step_start = time.time()
        chunks = self.database_service.save_chunks(chunks)
        print(f"Saved chunks to database: {time.time() - step_start}s")

        # Step 4: Generate embeddings
        step_start = time.time()
        chunks = self.embedding_service.generate_embeddings(chunks)
        print(f"Generated embeddings: {time.time() - step_start}s")

        # Step 5: Create FAISS index
        step_start = time.time()
        FaissUtils.create_faiss_index(chunks)
        print(f"Created FAISS index: {time.time() - step_start}s")

        total_time = time.time() - start_time
        print(f"\n=== Pipeline completed in {total_time}s ===")


if __name__ == "__main__":
    Config.validate()

    load_service = LoadService()
    load_service.run()
