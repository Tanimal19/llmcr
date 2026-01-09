"""RAG Application using Gemini API, MariaDB, and FAISS."""

import sys
from typing import List
from config import Config
from embedding_service import EmbeddingService
from mariadb_store import MariaDBVectorStore
from faiss_search import FAISSSearchService
from gemini_service import GeminiService


class RAGApplication:
    def __init__(self):
        Config.validate()

        # Initialize services
        self.embedding_service = EmbeddingService()
        self.db_store = MariaDBVectorStore()
        self.faiss_search = FAISSSearchService(
            embedding_dim=self.embedding_service.get_embedding_dimension()
        )
        self.gemini_service = GeminiService()

        # Sync FAISS index with database
        self._sync_index()

        print("=" * 60)
        print("RAG Application Ready!")
        print("=" * 60)

    def _sync_index(self):
        """Synchronize FAISS index with database."""
        doc_ids, embeddings = self.db_store.get_all_embeddings()

        if len(doc_ids) > 0:
            print(f"Syncing {len(doc_ids)} documents to FAISS index...")
            self.faiss_search.rebuild_index(embeddings, doc_ids)
        else:
            print("No documents in database yet.")

    def add_documents(self, documents: List[str], metadata: List[dict] = None):
        """
        Add documents to the knowledge base.

        Args:
            documents: List of document texts.
            metadata: Optional list of metadata dictionaries.
        """
        if not documents:
            print("No documents to add.")
            return

        print(f"\nAdding {len(documents)} documents...")

        # Generate embeddings
        print("Generating embeddings...")
        embeddings = self.embedding_service.encode(documents)

        # Store in database and FAISS
        doc_ids = []
        for i, (doc, emb) in enumerate(zip(documents, embeddings)):
            meta = metadata[i] if metadata and i < len(metadata) else None
            doc_id = self.db_store.add_document(doc, emb, meta)
            doc_ids.append(doc_id)

        # Update FAISS index
        self.faiss_search.add_vectors(embeddings, doc_ids)

        # Save FAISS index
        self.faiss_search.save_index()

        print(f"Successfully added {len(documents)} documents!")
        print(f"Total documents in database: {self.db_store.get_document_count()}")

    def query(self, question: str, top_k: int = 3) -> str:
        """
        Query the RAG system.

        Args:
            question: User's question.
            top_k: Number of relevant documents to retrieve.

        Returns:
            Generated answer.
        """
        print(f"\nQuery: {question}")
        print("-" * 60)

        # Generate query embedding
        query_embedding = self.embedding_service.encode(question)[0]

        # Search for similar documents
        doc_ids, distances = self.faiss_search.search(query_embedding, k=top_k)

        if not doc_ids:
            return (
                "No documents found in the knowledge base. Please add documents first."
            )

        # Retrieve documents from database
        documents = self.db_store.get_documents_by_ids(doc_ids)

        print(f"Retrieved {len(documents)} relevant documents")
        for i, (doc, dist) in enumerate(zip(documents, distances)):
            print(f"  {i+1}. Document ID: {doc['id']}, Distance: {dist:.4f}")

        # Generate response using Gemini
        print("\nGenerating response with Gemini...")
        response = self.gemini_service.generate_response(question, documents)

        return response

    def clear_knowledge_base(self):
        """Clear all documents from the knowledge base."""
        confirm = input("Are you sure you want to clear all documents? (yes/no): ")
        if confirm.lower() == "yes":
            self.db_store.clear_all()
            self.faiss_search.clear()
            self.faiss_search.save_index()
            print("Knowledge base cleared!")
        else:
            print("Operation cancelled.")

    def show_stats(self):
        """Display statistics about the knowledge base."""
        doc_count = self.db_store.get_document_count()
        index_size = self.faiss_search.get_index_size()

        print("\n" + "=" * 60)
        print("Knowledge Base Statistics")
        print("=" * 60)
        print(f"Total documents in database: {doc_count}")
        print(f"Total vectors in FAISS index: {index_size}")
        print(
            f"Embedding dimension: {self.embedding_service.get_embedding_dimension()}"
        )
        print("=" * 60)

    def close(self):
        """Clean up resources."""
        self.db_store.close()
        print("\nRAG Application closed.")


def print_menu():
    """Print the main menu."""
    print("\n" + "=" * 60)
    print("RAG Application Menu")
    print("=" * 60)
    print("1. Add documents to knowledge base")
    print("2. Query the knowledge base")
    print("3. Show statistics")
    print("4. Clear knowledge base")
    print("5. Exit")
    print("=" * 60)


def main():
    """Main entry point."""
    try:
        app = RAGApplication()

        while True:
            print_menu()
            choice = input("\nEnter your choice (1-5): ").strip()

            if choice == "1":
                print("\nEnter documents (one per line, empty line to finish):")
                documents = []
                while True:
                    doc = input("> ")
                    if not doc:
                        break
                    documents.append(doc)

                if documents:
                    app.add_documents(documents)
                else:
                    print("No documents entered.")

            elif choice == "2":
                question = input("\nEnter your question: ").strip()
                if question:
                    answer = app.query(question)
                    print("\n" + "=" * 60)
                    print("Answer:")
                    print("=" * 60)
                    print(answer)
                    print("=" * 60)
                else:
                    print("No question entered.")

            elif choice == "3":
                app.show_stats()

            elif choice == "4":
                app.clear_knowledge_base()

            elif choice == "5":
                print("\nExiting...")
                app.close()
                break

            else:
                print("Invalid choice. Please enter 1-5.")

    except KeyboardInterrupt:
        print("\n\nInterrupted by user.")
        sys.exit(0)
    except Exception as e:
        print(f"\nError: {e}")
        import traceback

        traceback.print_exc()
        sys.exit(1)


if __name__ == "__main__":
    main()
