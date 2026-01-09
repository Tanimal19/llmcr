```
export MARIADB_CONFIG=/opt/homebrew/opt/mariadb-connector-c/bin/mariadb_config
```


# RAG Application

A Retrieval-Augmented Generation (RAG) application built with:
- **Gemini API** for natural language generation
- **MariaDB** for vector database storage
- **FAISS** for efficient similarity search
- **Sentence-Transformers** for text embeddings

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      RAG Application                        │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  User Query ──> Embedding ──> FAISS Search ──> MariaDB     │
│                                      │                      │
│                                      v                      │
│                           Retrieved Documents               │
│                                      │                      │
│                                      v                      │
│                    Gemini API (Context + Query)            │
│                                      │                      │
│                                      v                      │
│                           Generated Response                │
└─────────────────────────────────────────────────────────────┘
```

## Components

### 1. Embedding Service (`embedding_service.py`)
- Uses Sentence-Transformers to generate vector embeddings
- Default model: `all-MiniLM-L6-v2` (384-dimensional embeddings)
- Converts text documents into numerical vectors

### 2. MariaDB Vector Store (`mariadb_store.py`)
- Stores documents and their metadata
- Stores embeddings as binary blobs
- Provides CRUD operations for documents
- Automatic database and table creation

### 3. FAISS Search Service (`faiss_search.py`)
- Fast similarity search using FAISS IndexFlatL2
- Supports index persistence to disk
- Returns k-nearest neighbors based on L2 distance

### 4. Gemini Service (`gemini_service.py`)
- Integrates with Google's Gemini API
- Generates context-aware responses
- Formats prompts with retrieved documents

### 5. Main Application (`main.py`)
- Interactive CLI interface
- Orchestrates all components
- Provides document management and querying

## Prerequisites

1. **Python 3.11+**
2. **MariaDB Server** running locally or remotely
3. **Gemini API Key** from [Google AI Studio](https://makersuite.google.com/app/apikey)

## Installation

### 1. Install Dependencies

Using `uv` (recommended):
```bash
cd python-app
uv sync
```

Or using `pip`:
```bash
pip install -r requirements.txt
```

### 2. Setup MariaDB

Start MariaDB server (if using Docker):
```bash
docker run -d \
  --name mariadb-rag \
  -e MYSQL_ROOT_PASSWORD=your_password \
  -e MYSQL_DATABASE=rag_database \
  -p 3306:3306 \
  mariadb:latest
```

Or use an existing MariaDB installation.

### 3. Configure Environment

Copy the example environment file:
```bash
cp .env.example .env
```

Edit `.env` and add your credentials:
```env
GEMINI_API_KEY=your_gemini_api_key_here
MARIADB_HOST=localhost
MARIADB_PORT=3306
MARIADB_USER=root
MARIADB_PASSWORD=your_password_here
MARIADB_DATABASE=rag_database
```

## Usage

### Start the Application

```bash
python main.py
```

### Menu Options

#### 1. Add Documents to Knowledge Base
Add text documents that will be used as context for answering questions:
```
1. Add documents to knowledge base
Enter documents (one per line, empty line to finish):
> The Eiffel Tower is located in Paris, France.
> It was built in 1889 and stands 330 meters tall.
> 
```

#### 2. Query the Knowledge Base
Ask questions about the documents you've added:
```
2. Query the knowledge base
Enter your question: Where is the Eiffel Tower located?
```

The system will:
1. Convert your question to an embedding
2. Search for similar documents using FAISS
3. Retrieve relevant documents from MariaDB
4. Generate a response using Gemini API with context

#### 3. Show Statistics
View information about your knowledge base:
```
3. Show statistics
Total documents in database: 10
Total vectors in FAISS index: 10
Embedding dimension: 384
```

#### 4. Clear Knowledge Base
Remove all documents and reset the system:
```
4. Clear knowledge base
Are you sure you want to clear all documents? (yes/no): yes
```

#### 5. Exit
Close the application and clean up resources.

## Advanced Usage

### Programmatic Usage

```python
from config import Config
from embedding_service import EmbeddingService
from mariadb_store import MariaDBVectorStore
from faiss_search import FAISSSearchService
from gemini_service import GeminiService

# Initialize services
embedding_service = EmbeddingService()
db_store = MariaDBVectorStore()
faiss_search = FAISSSearchService(embedding_service.get_embedding_dimension())
gemini_service = GeminiService()

# Add documents
documents = [
    "Python is a programming language.",
    "Machine learning is a subset of AI."
]
embeddings = embedding_service.encode(documents)

doc_ids = []
for doc, emb in zip(documents, embeddings):
    doc_id = db_store.add_document(doc, emb)
    doc_ids.append(doc_id)

faiss_search.add_vectors(embeddings, doc_ids)

# Query
question = "What is Python?"
query_emb = embedding_service.encode(question)[0]
doc_ids, distances = faiss_search.search(query_emb, k=3)
documents = db_store.get_documents_by_ids(doc_ids)
response = gemini_service.generate_response(question, documents)
print(response)
```

### Custom Configuration

You can override default settings in `.env`:

```env
# Use a different embedding model
EMBEDDING_MODEL=sentence-transformers/all-mpnet-base-v2

# Change FAISS index location
FAISS_INDEX_PATH=./custom_index_path
```

## Database Schema

### documents table
```sql
CREATE TABLE documents (
    id INT AUTO_INCREMENT PRIMARY KEY,
    content TEXT NOT NULL,
    metadata JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
)
```

### embeddings table
```sql
CREATE TABLE embeddings (
    id INT AUTO_INCREMENT PRIMARY KEY,
    doc_id INT NOT NULL,
    embedding BLOB NOT NULL,
    embedding_dim INT NOT NULL,
    FOREIGN KEY (doc_id) REFERENCES documents(id) ON DELETE CASCADE
)
```

## Performance Considerations

### FAISS Index Types

The application uses `IndexFlatL2` for exact search. For larger datasets (>100K documents), consider:

- **IndexIVFFlat**: Faster search with slight accuracy trade-off
- **IndexHNSWFlat**: Graph-based index for efficient approximate search

Modify `faiss_search.py`:
```python
# For IVF index:
quantizer = faiss.IndexFlatL2(self.embedding_dim)
self.index = faiss.IndexIVFFlat(quantizer, self.embedding_dim, 100)

# For HNSW index:
self.index = faiss.IndexHNSWFlat(self.embedding_dim, 32)
```

### Embedding Models

Choose based on your needs:
- `all-MiniLM-L6-v2`: Fast, 384-dim (default)
- `all-mpnet-base-v2`: Better quality, 768-dim
- `multi-qa-mpnet-base-dot-v1`: Optimized for Q&A

## Troubleshooting

### MariaDB Connection Errors
```
Error: Failed to connect to MariaDB
```
- Check MariaDB is running: `mysql -u root -p`
- Verify credentials in `.env`
- Ensure database exists or app has CREATE permissions

### Gemini API Errors
```
Error generating response: API key not valid
```
- Verify your API key in `.env`
- Check API quota at [Google AI Studio](https://makersuite.google.com/)

### FAISS Index Issues
```
Failed to load index
```
- Delete existing index files: `rm faiss_index.*`
- Restart the application to rebuild

## Dependencies

- `google-generativeai`: Gemini API client
- `mariadb`: MariaDB Python connector
- `faiss-cpu`: Vector similarity search
- `sentence-transformers`: Text embedding generation
- `numpy`: Numerical operations
- `python-dotenv`: Environment configuration

## License

MIT License

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.
