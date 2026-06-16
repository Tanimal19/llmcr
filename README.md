# Intelligent Code Review System: LLMCR

## Datasets used
- Project Context:
  - Spring AI (git@github.com:spring-projects/spring-ai.git)
  - including source code, documentation, issues, pull requests
- Review Guidelines: Writen by human, references:
  - https://google.github.io/eng-practices/review/reviewer/standard.html
  - https://github.com/mawrkus/pull-request-review-guide
  - https://levelup.gitconnected.com/the-ultimate-guideline-for-a-good-code-review-1588bc2979fc
  - https://owasp.org/www-project-code-review-guide/assets/OWASP_Code_Review_Guide_v2.pdf
- Other Knowledge:
  - Java Best Practice: Effective Java 3rd Edition by Joshua Bloch
  - Design Patterns: https://github.com/nilbuild/design-patterns-for-humans
  - Code Smells: https://github.com/Luzkan/smells/tree/main/content/smells
  - Security: https://github.com/OWASP/Top10/tree/master/2025/docs/en

## Data Schema
- `TrackRoot`: represents a specific folder or file that we want to track.
  - A `TrackRoot` can be configure with `allowed_source_types` defines what type of data source to be included when tracking.
- `Source`: represents a specific file that we want to extract data from.
- `Context`: a paragraph of meaningful text.
  - It can be an entire Java class, a paragraph in a document, or some defined structure.
  - The retrieval result is a list of `Context`.
- `Chunk`: a smaller paragraph of text that is stored in a vector database as embedding.
  - The similarity search result is a list of `Chunk`.
  - Metadata:
    ```yml
    parent_trackroot:
    parent_source:
    parent_context:
    type: project_context, review_guideline, knowledge
    cutoff_date: only for github pull requests and issues
    ```



## ETL and RAG
The ETL pipeline is responsible for extracting data from various sources, processing it into `Context`, and storing it in the vector database as `Chunk`. The RAG workflow retrieves relevant `Chunk` from the vector database based on the input query, and use them as context for agents to generate answer.


