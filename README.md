# Intelligent Code Review RAG Application

Project for Software Engineering Lab.  
Detail: https://drive.google.com/file/d/1ROs21oOD5hAumyx3W9JTEB31CfwmOUw5/view?usp=share_link


# Run
To run the application, follow these steps:
- Make sure llama.cpp is installed.
- Start llamacpp server
```sh
llama-server \
  --models-dir models/ \
  --models-max 1 \
  --ctx-size 8192 \
  --batch-size 512 \
  --ubatch-size 128 \
  --parallel 1 \
  --threads -1 \
  --flash-attn on \
  --host 0.0.0.0 \
  --port 8080
```
- Start the FAISS and MariaDB services using `docker-compose up -d`.
- Run the application using `./run.sh` in the `spring-app` directory. (`cd spring-app` first)

## llama-server

Start embedding, reranking and chat server:
```sh
llama-server -m {chatmodel_name}.gguf --ctx-size 16384 --batch-size 512 --ubatch-size 128 --parallel 1 --host 0.0.0.0 --port {port}
llama-server -m {embedding_model_name}.gguf --embeddings --ctx-size 16384 --batch-size 512 --ubatch-size 128 --parallel 1 --host 0.0.0.0 --port {port}
llama-server -m {reranking_model_name}.gguf --reranking --ctx-size 16384 --batch-size 512 --ubatch-size 128 --parallel 1 --host 0.0.0.0 --port {port}
```


Sample request for embedding:
```sh
curl http://localhost:8080/v1/embeddings \
  -H "Content-Type: application/json" \
  -d '{
    "model": "harrier-oss-v1-0.6b.Q8_0",
    "input": ["What is retrieval augmented generation?"]
  }'
```

Sample request for reranking:
```sh
curl http://localhost:8080/v1/rerank \
  -H "Content-Type: application/json" \
  -d '{
    "model": "zerank-2.Q8_0",
    "query": "What is retrieval augmented generation?",
    "documents": [
      "RAG combines retrieval and generation using LLMs.",
      "Transformers are neural networks introduced in 2017.",
      "Vector databases store embeddings for similarity search."
    ]
  }'
```


## ETL Pipeline
To run the ETL pipeline, set the `--app.mode=etl` in `run.sh`.  
You need to set the paths of Java project and documentation in `run.sh` as well.
```sh
SPRING_ARGUMENTS="--app.mode=etl \
    --etl.input.javaproject.path=\"../_datasets/spring-ai-main simple\" \
    --etl.input.document.paths=\"../_datasets/spring-ai-docs/src/main/antora/modules/ROOT/pages/,../_datasets/Effective Java (2017, Addison-Wesley).pdf\""
```

The ETL process consists of three main steps, which are implemented in the following classes:
- `service/etl/ExtractService.java`: Extract class node from `.java` and paragraphs from docs.
- `service/etl/TransformService.java`: Enrich each class node with paragraphs and generate summary via LLM.
- `service/etl/LoadService.java`: Chunk and Load the enriched class nodes and paragraphs into FAISS vector store and MariaDB.


## RAG
To run the RAG application, set the `--app.mode=rag` in `run.sh`.  
This will start a CLI interface for you to ask questions about the Java project and documentation.

> [!NOTE]
> You can use the extracted data at `_backups/` to run the RAG application without running the ETL pipeline.  
> Place `.index` file under `./faiss_service/app/data` and import `.sql` file to MariaDB.  
> For example, run the following to import DB.  
> ```sh
> docker exec -i mariadb mariadb -u user -p123 ragdb < ragdb_backup.sql
> ```

# Configuration
- Set FAISS and MariaDB configurations in `docker-compose.yml`.
  - The index file of FAISS is stored in `./faiss_service/app/data`.
  - The database data is stored in docker volume, you can backup it via:
    ```sh
    docker exec mariadb mariadb-dump -u root -proot123 ragdb > ragdb_backup.sql
    ```
- Set spring app properties at `application.properties`.
- Set environment variables at `.env` file.
  ```sh
  export DB_USERNAME="user"
  export DB_PASSWORD="123"
  export GOOGLE_GEMINI_API_KEY="???"
  ```

## File Syntax
### Documents
Documents are unstructured files that provide additional information about the codebase, such as design documents, API documentation ...  
No syntax, accept `.pdf`, `.md`, `.asciidoc` files for now.

### Guideline
Guideline is a special type of context that describe code review guidelines.

```json
[
    {
        "id": 1,
        "guideline": "Guideline description",
    },
    // more guidelines
]
```

### Usecase
Usecase is an example on how to perform specific code review check.  

```json
[
    {
        "id": 1,
        "description": "Usecase description",
        "usecase": {
            "input": "some check list item",
            "output": "expected answer",
        }
    },
]
```


### ToolAction
ToolAction is a special type of context that describe the usage of a specific action, where each action maps to a java method.
All avaliable actions are pre-defined in `ToolActionRegistry.java`.


# Structure
- `_datasets/`: Datasets for ETL. (you need to prepare it by yourself)
- `faiss_service/`: FAISS vector store service implemented in Python Flask.
- `spring-app/`: Spring Boot application for ETL and RAG.
- `evaluation/`: Evaluation scripts and data.

## Java classes
- `datasource/`: Represents different type of data sources.
- `entity/`: Database schema entities.
- `extractor/`: Extractor that extract specific data from data sources.
- `repository/`: Repository for database operations.
- `service/etl/`: Classes for ETL pipeline.
- `service/faiss/`: Encapsulate FAISS operations as Spring AI's Vector Store.
- `service/rag/`: Classes for RAG application.
  - `RAGTemplate.java`: Base class for RAG template, indicate the use case of RAG.
  - `RetrievalStrategy.java`: Base class for retrieval strategy.

## Database Schema


# Common Problems
#### Can not find JAVA_HOME (Windows)
1. Set `JAVA_HOME` in System Environment Variables, e.g. `C:\Program Files\Java\jdk-xx `
2. Please use git bash instead of WSL bash/sh in powershell (WSL bash cannot find your JAVA_HOME)，add git bash in `PATH` System Environment Variables, e.g. `C:\Program Files\Git\bin`
