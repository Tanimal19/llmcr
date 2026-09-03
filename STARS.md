# STAR Log — LLMCR (Intelligent Code Review System)
> Auto-maintained accomplishment record. Run /stars to refresh.

_Last updated: 2026-07-18 (evidence through commit a7542f8)_

Note: multi-author repo (bob cheng, C-W-Z, gary). Items below cover work authored by Bob Cheng (~322 of 440 commits).

## 2026

### Designed a multi-agent LLM code review workflow with draft-then-prune refinement
*2026-05 → 2026-06 · evidence: PRs #3–#7, #23, #25; commits c782398…7e3c312*

- **S**: Single-prompt LLM code review produced shallow, noisy findings; the project needed a structured review pipeline grounded in project context.
- **T**: Design and implement the agent architecture for generating code review reports.
- **A**: Built an agent framework (abstract `Agent` base, model client factory, custom tool-calling manager) with cooperating summary, computation, retrieval, and question-answer agents; implemented a draft-then-prune strategy that generates candidate findings and prunes low-quality ones, plus a single-agent mode as baseline; added retry mechanisms and per-agent review trace logging.
- **R**: End-to-end multi-agent review pipeline producing structured JSON reports, with single-agent baseline for comparison. > TODO: add metric (e.g., precision/recall vs. baseline from evaluation runs)

**Resume bullet**: Designed a multi-agent LLM code review workflow (summary/retrieval/computation agents with draft-then-prune refinement) producing structured, context-grounded review reports.

### Built a RAG pipeline over a real-world Java codebase and its docs
*2026-01 → 2026-04 · evidence: commits bb0cf82…68af3c3 (ETL: 22b2bff, cf14405; retrieval: 1ba2416, 8f269f6)*

- **S**: Review agents needed grounding in the target project (Spring AI source, docs, issues, PRs) plus review guidelines and Java best-practice references — heterogeneous sources with no unified representation.
- **T**: Own data ingestion and retrieval end to end.
- **A**: Designed a `TrackRoot → Source → Context → Chunk` data schema and a Spring-based ETL pipeline (extract/split/enrich/load as separate services) with extractors for Java classes, AsciiDoc, and Markdown; implemented retrieval with reranking, fusion strategy, adaptive-k selection, and a RAG advisor; added incremental source sync so re-ingestion only processes changed files.
- **R**: Unified retrieval across 7 context types (code, docs, issues, PRs, guidelines, background knowledge) feeding the review agents. > TODO: add metric (e.g., corpus size, retrieval hit-rate from evaluation)

**Resume bullet**: Built an end-to-end RAG pipeline (custom ETL, Java/AsciiDoc/Markdown extractors, reranking + adaptive-k retrieval) unifying source code, docs, issues, and PRs into one retrieval layer.

### Stood up an isolated FAISS vector-store microservice
*2026-01 → 2026-06 · evidence: commits 673b226, ba32456, 84a0506, 74f7dac*

- **S**: The JVM app needed fast vector similarity search, but FAISS is Python/C++ native with no good JVM binding.
- **T**: Provide vector storage/search to the Spring backend without coupling it to Python tooling.
- **A**: Built a standalone Python FAISS service with an HTTP API, Dockerized it and wired it into docker-compose; later consolidated storage into a single main index with ID-based filtering instead of per-collection index files, and fixed a security issue in the service.
- **R**: Language-isolated vector search reused by both ETL load and RAG retrieval; simplified index management to one file. > TODO: add metric (e.g., index size, query latency)

**Resume bullet**: Built and Dockerized a standalone FAISS vector-search microservice with ID-filtered single-index storage, decoupling JVM application code from native vector-search tooling.

### Built an evaluation framework benchmarking the system against commercial review bots
*2026-05 → 2026-06 · evidence: PRs #8, #21, #22, #24; commits 8149974, 09361af, e90c6bf, b09811f, 79bc066*

- **S**: No objective way to judge review quality or compare the system against existing tools (CodeRabbit, GitHub Copilot review).
- **T**: Create the evaluation methodology, datasets, and tooling.
- **A**: Wrote collectors for real GitHub PRs (with changed-file filters) and for bot reviews from CodeRabbit and Copilot; implemented comment-alignment scoring combining Jaccard similarity with NLI, plus an SLM-based alignment evaluator; added token-cost estimation, a pre-evaluation step, and an evaluation dashboard with report preview.
- **R**: Repeatable benchmark comparing multi-agent, single-LLM, and commercial bot reviews on real PRs. > TODO: add headline numbers (alignment scores, PR count evaluated)

**Resume bullet**: Built an LLM-review evaluation framework (Jaccard + NLI + SLM alignment scoring) benchmarking the system against CodeRabbit and Copilot on real GitHub PRs.

### Turned the pipeline into a client-server product: SSE-streaming API + terminal UI
*2026-05 · evidence: PRs #9, #10; commits 4c6ae15, b1e5cc7, b325d0b, d798126*

- **S**: The system only ran as batch scripts; long-running review and sync jobs had no interactive interface or progress feedback.
- **T**: Expose the backend as an API and build a usable frontend.
- **A**: Converted the Spring app into an API server with a global exception handler and typed error codes; built an SSE task manager streaming progress of review and sync jobs; wrote a React Ink terminal UI with commands for running reviews, browsing the database, configuring RAG, and previewing reports.
- **R**: Interactive TUI client with live-streamed progress over SSE for long-running review/sync tasks. > TODO: add metric (e.g., typical review duration surfaced to users)

**Resume bullet**: Productized a batch ML pipeline into a Spring API server with SSE progress streaming and a React Ink terminal client.

### Integrated static analysis as LLM-callable tools and hardened the backend
*2026-05 · evidence: PRs #11–#17, #20; commits d19d331…75aa5e7, 51d1f05, cba726b*

- **S**: Reviews relied purely on LLM judgment, and the fast-growing backend had accumulated structural debt and code-quality warnings.
- **T**: Give agents deterministic code-analysis capabilities and clean up the backend.
- **A**: Built static-analysis tool classes with unit tests and registered them as tools the LLM agents can invoke; drove a backend-wide refactor (exception handling, sync services, file structure, SSE task objects) resolving SonarQube reliability/security findings; set up Spotless with google-java-format and tightened the GitHub Actions workflow (Java 21, path-filtered triggers).
- **R**: Agents can ground findings in deterministic analysis; CI-enforced formatting and reduced static-analysis findings. > TODO: add metric (e.g., # of Sonar issues resolved)

**Resume bullet**: Extended LLM review agents with deterministic static-analysis tool-calling and led a backend-wide refactor with CI-enforced formatting and SonarQube cleanup.
