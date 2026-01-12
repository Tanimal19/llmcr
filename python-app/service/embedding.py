import sys
import numpy as np
from sentence_transformers import SentenceTransformer
from config import Config
from typing import List
from service.database import Chunk, Document


class EmbeddingService:
    def __init__(self):
        try:
            self.model = SentenceTransformer(
                Config.EMBEDDING_MODEL_PATH,
                tokenizer_kwargs={"fix_mistral_regex": True},
            )
            print(f"Loaded embedding model from {Config.EMBEDDING_MODEL_PATH}")

        except Exception as e:
            print(f"Error loading embedding model: {e}")
            sys.exit(1)

    def generate_chunks(
        self, documents: List[Document], chunk_size=300, overlap=50
    ) -> List[Chunk]:
        tokenizer = self.model.tokenizer
        chunks = []

        for doc in documents:
            if doc.metadata["type"] != "paragraph":
                chunks.append(
                    Chunk(
                        id=None,
                        metadata=doc.metadata,
                        content=doc.content,
                        source_id=doc.source_id,
                        embedding=None,
                    )
                )
                print(f"- skip chunking for non-paragraph document: {doc.source_id}")
                continue

            tokens = tokenizer.encode(doc.content, add_special_tokens=False)
            print(f"+ chunking document {doc.source_id} with {len(tokens)} tokens.")
            start = 0

            while start < len(tokens):
                end = min(start + chunk_size, len(tokens))
                chunk_tokens = tokens[start:end]
                chunk_text = tokenizer.decode(chunk_tokens)

                chunks.append(
                    Chunk(
                        id=None,
                        metadata=doc.metadata,
                        content=chunk_text,
                        source_id=doc.source_id,
                        embedding=None,
                    )
                )

                start += chunk_size - overlap
                if start < 0:
                    start = 0

        print(f"Generated {len(chunks)} chunks from {len(documents)} documents.")

        return chunks

    def generate_embeddings(self, chunks: List[Chunk]) -> List[Chunk]:
        print(f"Generating embeddings ...")
        for idx, chunk in enumerate(chunks):
            embedding = self.model.encode_document(chunk.content)
            chunk.embedding = np.array(embedding, dtype=np.float32)

            if (idx + 1) % 100 == 0:
                print(f"  Progress: {idx + 1}/{len(chunks)} chunks")

        return chunks
