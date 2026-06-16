# Intelligent Code Review System: LLMCR

## Datasets used
- Project Context:
  - Spring AI (git@github.com:spring-projects/spring-ai.git) including source code, documentation, issues, pull requests
  - source code -> Java classes, coding conventions
  - documentation -> Java classes usage
  - issues -> historical & current issues (to see the motivation behind code changes)
  - pull requests -> review decision considerations, current & future plans (to see if the change aligns with the project vision)

- Review Guidelines: Writen by human, references:
  - https://google.github.io/eng-practices/review/reviewer/standard.html
  - https://github.com/mawrkus/pull-request-review-guide
  - https://levelup.gitconnected.com/the-ultimate-guideline-for-a-good-code-review-1588bc2979fc
  - https://owasp.org/www-project-code-review-guide/assets/OWASP_Code_Review_Guide_v2.pdf

- Background Knowledge:
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
  - Metadata:
    ```yml
    parent_trackroot:
    parent_source:
    type: project_code, project_doc, project_issue, project_pr, project_plan, project_rule, review_guideline, bg_knowledge
    cutoff_date:
    ```
- `Chunk`: a smaller paragraph of text that is stored in a vector database as embedding.
  - The similarity search result is a list of `Chunk`.
  - Metadata:
    ```yml
    parent_trackroot:
    parent_source:
    parent_context:
    type: project_code, project_doc, project_issue, project_pr, project_plan, project_convention, project_decision, review_guideline, bg_knowledge
    cutoff_date:
    ```



## ETL and RAG
The ETL pipeline is responsible for extracting data from various sources, processing it into `Context`, and storing it in the vector database as `Chunk`. The RAG workflow retrieves relevant `Chunk` from the vector database based on the input query, and use them as context for agents to generate answer.


