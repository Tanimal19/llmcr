from pathlib import Path
from enum import Enum


class EvaluationGroup(Enum):
    CODERABBIT = "coderabbit"
    COPILOT = "copilot"
    SINGLELLM = "singlellm"
    LLMCR = "llmcr"


DATA_DIR = Path(__file__).parent / "data"

BOT_REVIEW_PR_URLS_CSV = DATA_DIR / "settings" / "bot_review_pr_urls.csv"

RAW_PULL_REQUEST_JSONL = DATA_DIR / "exports" / "raw_pull_request.jsonl"
REPHRASED_PULL_REQUEST_JSONL = DATA_DIR / "exports" / "rephrased_pull_request.jsonl"

REVIEW_SOURCE_DIR = lambda group_name: DATA_DIR / "reviews" / group_name
MERGED_REVIEW_JSONL = (
    lambda group_name: DATA_DIR / "reviews" / group_name / "merged_review.jsonl"
)

EVALUATION_RESULT_JSONL = (
    lambda group_name: DATA_DIR / "reviews" / group_name / "evaluation_results.jsonl"
)
