# RAG Application

## Load Service
1. Load documents from database
2. Preprocess documents (splitting into chunks)
   - parameters: chunk size, overlap size
3. Generate embeddings for document chunks using llama-cpp
4. Generate index using FAISS and store into `faiss.index` file

## RAG Service
1. Load FAISS index from `faiss.index` file
2. Generate embedding for the query using llama-cpp
3. Retrieve top-k relevant document indices from FAISS index
4. Fetch corresponding document chunks from database
5. Generate answer using Gemini API based on the retrieved documents and user query


## Database Schema
```
create table ragdb.chunks
(
    id        bigint unsigned auto_increment
        primary key,
    content   text not null,
    source_id uuid not null
);

create table ragdb.class_nodes
(
    id                varchar(255) not null
        primary key,
    code_text         text         null,
    description_text  text         null,
    processed         bit          null,
    relationship_text text         null,
    signature         text         null,
    usage_text        text         null
);

create table ragdb.document_paragraphs
(
    id      varchar(255) not null
        primary key,
    content text         null,
    source  varchar(255) null
);

create table ragdb.class_node_document_paragraph
(
    class_node_id         varchar(255) not null,
    document_paragraph_id varchar(255) not null,
    constraint FKjnn99woxulxdtadowmmcq4uyh
        foreign key (class_node_id) references ragdb.class_nodes (id),
    constraint FKs4sr9ryu2xbe628dhea69y700
        foreign key (document_paragraph_id) references ragdb.document_paragraphs (id)
);
```