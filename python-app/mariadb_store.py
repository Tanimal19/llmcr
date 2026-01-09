import mariadb
import json
import numpy as np
from typing import List, Dict, Optional, Tuple
from config import Config


class MariaDBVectorStore:
    def __init__(self):
        self.connection = None
        self.connect()

    def connect(self):
        try:
            self.connection = mariadb.connect(
                host=Config.MARIADB_HOST,
                port=Config.MARIADB_PORT,
                user=Config.MARIADB_USER,
                password=Config.MARIADB_PASSWORD,
                database=Config.MARIADB_DATABASE,
            )
            print(
                f"Connected to MariaDB at {Config.MARIADB_HOST}:{Config.MARIADB_PORT}"
            )
        except mariadb.Error as e:
            raise Exception(f"Failed to connect to MariaDB: {e}")

    def get_all_embeddings(self) -> Tuple[List[int], np.ndarray]:
        """
        Retrieve all embeddings from the database.

        Returns:
            Tuple of (doc_ids, embeddings_array)
        """
        cursor = self.connection.cursor()
        cursor.execute("SELECT doc_id, embedding, embedding_dim FROM embeddings")

        doc_ids = []
        embeddings = []

        for row in cursor:
            doc_id, embedding_blob, embedding_dim = row
            embedding = np.frombuffer(embedding_blob, dtype=np.float32).reshape(
                embedding_dim
            )
            doc_ids.append(doc_id)
            embeddings.append(embedding)

        cursor.close()

        if embeddings:
            embeddings_array = np.vstack(embeddings)
        else:
            embeddings_array = np.array([])

        return doc_ids, embeddings_array

    def get_document_count(self) -> int:
        """Get total number of documents."""
        cursor = self.connection.cursor()
        cursor.execute("SELECT COUNT(*) FROM documents")
        count = cursor.fetchone()[0]
        cursor.close()
        return count

    def close(self):
        """Close database connection."""
        if self.connection:
            self.connection.close()
            print("MariaDB connection closed")
