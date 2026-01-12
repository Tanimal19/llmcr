import os
from dotenv import load_dotenv

load_dotenv(dotenv_path="../.env")


class Config:
    # Chat Model
    GEMINI_API_KEY = os.getenv("GOOGLE_GEMINI_API_KEY")
    CHAT_MODEL = "gemma-3-27b-it"

    # Embedding
    EMBEDDING_MODEL_PATH = "google/embeddinggemma-300m"

    # MariaDB
    MARIADB_HOST = "127.0.0.1"
    MARIADB_PORT = 3306
    MARIADB_DATABASE = "ragdb"
    MARIADB_USER = "user"
    MARIADB_PASSWORD = "123"

    # FAISS Index
    FAISS_INDEX_PATH = "./faiss.index"

    @classmethod
    def validate(cls):
        if not cls.GEMINI_API_KEY:
            raise ValueError("GEMINI_API_KEY not found.")
