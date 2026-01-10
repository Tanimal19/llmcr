import os
from dotenv import load_dotenv

load_dotenv()


class Config:
    GEMINI_API_KEY = os.getenv("GEMINI_API_KEY")
    CHAT_MODEL = "gemma-3-27b-it"

    # Embedding
    EMBEDDING_MODEL = "embeddinggemma-300m"
    EMBEDDING_MODEL_PATH = "./models/embeddinggemma-300m.gguf"
    EMBEDDING_DIM = 768

    # MariaDB
    MARIADB_HOST = "localhost"
    MARIADB_PORT = 3306
    MARIADB_DATABASE = "ragdb"
    MARIADB_USER = "user"
    MARIADB_PASSWORD = "123"

    # FAISS Index
    FAISS_INDEX_PATH = "./faiss_index.index"

    @classmethod
    def validate(cls):
        if not cls.GEMINI_API_KEY:
            raise ValueError("GEMINI_API_KEY not found.")
