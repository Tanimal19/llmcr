import mariadb
import sys
import numpy as np
from config import Config
from typing import List
from dataclasses import dataclass


@dataclass
class Chunk:
    id: int | None
    metadata: dict
    content: str
    source_id: str
    embedding: np.ndarray | None


@dataclass
class Document:
    metadata: dict
    content: str
    source_id: str


class DatabaseService:
    def __init__(self):
        try:
            self.connection = mariadb.connect(
                host=Config.MARIADB_HOST,
                port=Config.MARIADB_PORT,
                database=Config.MARIADB_DATABASE,
                user=Config.MARIADB_USER,
                password=Config.MARIADB_PASSWORD,
                connect_timeout=5,
            )
            print(f"Connected to MariaDB database: {Config.MARIADB_DATABASE}")
        except mariadb.Error as e:
            print(f"Error connecting to MariaDB: {e}")
            sys.exit(1)

    def load_documents(self) -> List[Document]:
        documents = []
        documents.extend(self._load_class_nodes_to_documents())
        documents.extend(self._load_paragraphs_to_documents())
        return documents

    def _load_class_nodes_to_documents(self) -> List[Document]:
        documents = []
        cursor = self.connection.cursor()

        try:
            cursor.execute(
                """
                SELECT id, code_text, description_text, 
                       relationship_text, usage_text 
                FROM class_nodes
                LIMIT 100
            """
            )
            for (
                node_id,
                code_text,
                description_text,
                relationship_text,
                usage_text,
            ) in cursor:
                if code_text:
                    documents.append(
                        Document(
                            metadata={"type": "code"},
                            content=code_text,
                            source_id=node_id,
                        )
                    )
                if description_text:
                    documents.append(
                        Document(
                            metadata={"type": "summary"},
                            content=description_text,
                            source_id=node_id,
                        )
                    )
                if relationship_text:
                    documents.append(
                        Document(
                            metadata={"type": "summary"},
                            content=relationship_text,
                            source_id=node_id,
                        )
                    )
                if usage_text:
                    documents.append(
                        Document(
                            metadata={"type": "summary"},
                            content=usage_text,
                            source_id=node_id,
                        )
                    )

            print(f"Loaded {len(documents)} documents from table class_nodes.")

        except mariadb.Error as e:
            print(f"Error loading class nodes: {e}")
            self.connection.rollback()
            sys.exit(1)
        finally:
            cursor.close()

        return documents

    def _load_paragraphs_to_documents(self) -> List[Document]:
        documents = []
        cursor = self.connection.cursor()

        try:
            cursor.execute("SELECT id, content FROM document_paragraphs LIMIT 10")
            for doc_id, content in cursor:
                if content:
                    documents.append(
                        Document(
                            metadata={"type": "paragraph"},
                            content=content,
                            source_id=doc_id,
                        )
                    )
            print(f"Loaded {len(documents)} documents from table document_paragraphs.")

        except mariadb.Error as e:
            print(f"Error loading paragraphs: {e}")
            self.connection.rollback()
            sys.exit(1)
        finally:
            cursor.close()

        return documents

    def save_chunks(self, chunks: List[Chunk]) -> List[Chunk]:
        cursor = self.connection.cursor()

        try:
            insert_query = """
                INSERT INTO chunks (source_id, content)
                VALUES (?, ?)
            """

            # Insert each chunk and collect the IDs
            for chunk in chunks:
                cursor.execute(insert_query, (chunk.source_id, chunk.content))
                chunk.id = cursor.lastrowid

            # Commit the transaction
            self.connection.commit()
            print(f"Stored {len(chunks)} chunks into the database.")

        except mariadb.Error as e:
            print(f"Error storing chunks: {e}")
            self.connection.rollback()
            sys.exit(1)
        finally:
            cursor.close()

        return chunks

    def close(self):
        if self.connection:
            self.connection.close()
            print("MariaDB connection closed.")
