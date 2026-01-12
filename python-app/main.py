from config import Config
import time
from service.embedding import EmbeddingService
from service.database import DatabaseService
from service.faiss import FaissService
from contextlib import contextmanager
from service.rag import RAGService
from service.chatbot import ChatbotService


@contextmanager
def timed_step(step_name):
    start = time.time()
    yield
    print(f"{step_name}: {time.time() - start:.2f}s")


def load(embedding_service: EmbeddingService, database_service: DatabaseService):
    print("Start load steps...")

    with timed_step("load documents"):
        documents = database_service.load_documents()

    with timed_step("chunk documents"):
        chunks = embedding_service.generate_chunks(
            documents, chunk_size=300, overlap=50
        )
        chunks = database_service.save_chunks(chunks)

    with timed_step("generate embeddings"):
        chunks = embedding_service.generate_embeddings(chunks)

    with timed_step("create FAISS index"):
        FaissService.create_index(chunks)


if __name__ == "__main__":
    Config.validate()

    embedding_service = EmbeddingService()
    database_service = DatabaseService()

    # load(embedding_service, database_service)

    faiss_service = FaissService()
    rag_service = RAGService(
        embedding_service,
        database_service,
        faiss_service,
    )

    chatbot_service = ChatbotService()

    # Example interaction
    user_query = "Explain the use of CompressionQueryTransformer."
    retrieved_chunks = rag_service.retrieve(user_query, top_k=5)
    prompt_template = (
        "Using the following context:\n{context}\nAnswer the question:\n{question}"
    )
    prompt = prompt_template.format(
        context="\n".join([chunk for chunk in retrieved_chunks]),
        question=user_query,
    )
    print(f"Prompt to chatbot:\n{prompt}\n")
    response = chatbot_service.chat(prompt)

    print(f"Chatbot response:\n{response}\n")
