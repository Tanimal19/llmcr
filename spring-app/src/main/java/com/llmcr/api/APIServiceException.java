package com.llmcr.api;

import org.springframework.http.HttpStatus;

public class APIServiceException extends RuntimeException {

    public enum ErrorCode {
        RAG_RETRIEVAL_FAILED("ragretrievalfailed", "Failed to retrieve contexts",
                HttpStatus.INTERNAL_SERVER_ERROR),
        RAG_SCOPE_GET_FAILED("ragscopegetfailed", "Failed to get RAG scope",
                HttpStatus.INTERNAL_SERVER_ERROR),
        RAG_SCOPE_SET_FAILED("ragscopesetfailed", "Failed to set RAG scope",
                HttpStatus.INTERNAL_SERVER_ERROR),
        RAG_VECTOR_SEARCH_FAILED("ragvectorsearchfailed", "Vector search failed",
                HttpStatus.INTERNAL_SERVER_ERROR),
        RAG_RERANK_FAILED("ragrerankfailed", "Reranking failed",
                HttpStatus.INTERNAL_SERVER_ERROR),
        RAG_CONTEXT_MERGE_FAILED("ragcontextmergefailed", "Failed to merge retrieved contexts",
                HttpStatus.INTERNAL_SERVER_ERROR),
        RAG_FUSION_FAILED("ragfusionfailed", "Failed to fuse multi-query retrieval results",
                HttpStatus.INTERNAL_SERVER_ERROR),
        RAG_TOPK_SELECTION_FAILED("ragtopkselectionfailed", "Failed to select top-k contexts",
                HttpStatus.INTERNAL_SERVER_ERROR),

        ETL_EXTRACT_FAILED("etlextractfailed", "ETL extract stage failed",
                HttpStatus.INTERNAL_SERVER_ERROR),
        ETL_SPLIT_FAILED("etlsplitfailed", "ETL split stage failed",
                HttpStatus.INTERNAL_SERVER_ERROR),
        ETL_ENRICH_FAILED("etlenrichfailed", "ETL enrich stage failed",
                HttpStatus.INTERNAL_SERVER_ERROR),
        ETL_LOAD_FAILED("etlloadfailed", "ETL load failed",
                HttpStatus.INTERNAL_SERVER_ERROR),
        ETL_RUN_FAILED("etlrunfailed", "ETL pipeline execution failed",
                HttpStatus.INTERNAL_SERVER_ERROR),

        REVIEW_PARSE_FAILED("reviewparsefailed", "Failed to parse pull request data",
                HttpStatus.INTERNAL_SERVER_ERROR),
        REVIEW_INTERPRETATION_FAILED("reviewinterpretationfailed", "Review interpretation stage failed",
                HttpStatus.INTERNAL_SERVER_ERROR),
        REVIEW_PLANNING_FAILED("reviewplanningfailed", "Review planning stage failed",
                HttpStatus.INTERNAL_SERVER_ERROR),
        REVIEW_COMPUTATION_FAILED("reviewcomputationfailed", "Review computation stage failed",
                HttpStatus.INTERNAL_SERVER_ERROR),
        REVIEW_SUMMARY_FAILED("reviewsummaryfailed", "Review summary stage failed",
                HttpStatus.INTERNAL_SERVER_ERROR),
        REVIEW_REPORT_WRITE_FAILED("reviewreportwritefailed", "Failed to write review report",
                HttpStatus.INTERNAL_SERVER_ERROR),
        REVIEW_PIPELINE_FAILED("reviewpipelinefailed", "Code review pipeline execution failed",
                HttpStatus.INTERNAL_SERVER_ERROR),

        CONFIG_SYNC_TRACK_ROOTS_FAILED("configsynctrackrootsfailed", "Failed to sync track roots",
                HttpStatus.INTERNAL_SERVER_ERROR),
        CONFIG_SYNC_COLLECTIONS_FAILED("configsynccollectionsfailed", "Failed to sync collections",
                HttpStatus.INTERNAL_SERVER_ERROR),
        CONFIG_SYNC_DEFAULT_COLLECTIONS_FAILED("configsyncdefaultcollectionsfailed",
                "Failed to sync default collections",
                HttpStatus.INTERNAL_SERVER_ERROR),

        SOURCE_SYNC_GET_SYNC_TIME_FAILED("sourcesyncgetsynctimefailed", "Failed to get last sync time",
                HttpStatus.INTERNAL_SERVER_ERROR),
        SOURCE_SYNC_PREVIEW_LIST_FAILED("sourcesyncpreviewlistfailed", "Failed to list track root previews",
                HttpStatus.INTERNAL_SERVER_ERROR),
        SOURCE_SYNC_FAILED("sourcesyncallfailed", "Failed to sync all track roots",
                HttpStatus.INTERNAL_SERVER_ERROR),
        SOURCE_SYNC_PREVIEW_FAILED("sourcesyncpreviewfailed", "Failed to generate track root preview",
                HttpStatus.INTERNAL_SERVER_ERROR),
        SOURCE_SYNC_LOCAL_SCAN_FAILED("sourcesynclocalscanfailed", "Failed to scan local sources",
                HttpStatus.INTERNAL_SERVER_ERROR),
        SOURCE_SYNC_HASH_FAILED("sourcesynchashfailed", "Failed to compute source content hash",
                HttpStatus.INTERNAL_SERVER_ERROR),
        SOURCE_SYNC_TRACK_ROOT_FAILED("sourcesynctrackrootfailed", "Failed to sync track root sources",
                HttpStatus.INTERNAL_SERVER_ERROR),
        SOURCE_SYNC_REMOVE_CHUNKS_FAILED("sourcesyncremovechunksfailed", "Failed to remove chunks from vector store",
                HttpStatus.INTERNAL_SERVER_ERROR),

        INVALID_REQUEST("invalidrequest", "Invalid request payload or parameters",
                HttpStatus.INTERNAL_SERVER_ERROR),
        INTERNAL_ERROR("internalerror", "An unexpected error occurred",
                HttpStatus.INTERNAL_SERVER_ERROR),

        LLM_RESPONSE_FAILED("llmresponsefailed", "Failed to get response from LLM",
                HttpStatus.INTERNAL_SERVER_ERROR),
        SSE_TASK_CANCELLED("ssetaskcancelled", "SSE task was cancelled",
                HttpStatus.CONFLICT);

        private final String code;
        private final String message;
        private final HttpStatus status;

        ErrorCode(String code, String message, HttpStatus status) {
            this.code = code;
            this.message = message;
            this.status = status;
        }

        public String code() {
            return code;
        }

        public String message() {
            return message;
        }

        public HttpStatus status() {
            return status;
        }
    }

    private final ErrorCode errorCode;

    public APIServiceException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public APIServiceException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public APIServiceException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.message(), cause);
        this.errorCode = errorCode;
    }

    public APIServiceException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public HttpStatus getStatus() {
        return errorCode.status();
    }
}
