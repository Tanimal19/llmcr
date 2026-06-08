import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import List
from dacite import from_dict
from share.utils import render_prompt_template
from share.llm import call_gemini
from share.code_review_scheme import CodeReviewEntry, CodeReviewContent
from share.pull_request_scheme import PullRequestEntry
from . import Evaluator

PROMPT_FILE = Path(__file__).with_name("laaj.prompt.txt")
MODEL = "gemini-3.1-pro-preview"


@dataclass
class LaajResult:
    comprehensiveness: int
    conciseness: int
    relevance: int
    topics_to_be_covered: List[str]
    step_by_step_analysis: List[str]


def _collect_review_content(review: CodeReviewContent) -> str:
    content_json = {
        "motivation": review.motivation,
        "good_points": review.good_points,
        "bad_points": review.bad_points,
        "suggestion": review.suggestion,
        "issues": [
            {
                "title": issue.title,
                "type": issue.issue_type,
                "detail": issue.detail,
                "location": issue.location,
            }
            for issue in review.issues
        ],
        "implementation_details": [
            {
                "filename": impl.filename,
                "details": impl.details,
            }
            for impl in review.implementation_details
        ],
    }

    return json.dumps(content_json, ensure_ascii=False, indent=2)


def _collect_pr_content(pr: PullRequestEntry) -> str:
    content_json = {
        "title": pr.title,
        "description": pr.description,
        "changed_files": [
            {
                "path": file.path,
                "previous_path": file.previous_path,
                "patch": file.patch,
            }
            for file in pr.changed_files
        ],
    }

    return json.dumps(content_json, ensure_ascii=False, indent=2)


def _collect_pr_comments(pr: PullRequestEntry) -> str:
    comments_json = {
        "comments": [
            {
                "poster": comment.poster,
                "body": comment.body,
                "diff": comment.diff_content,
            }
            for comment in pr.comments
        ],
        "is_approved": pr.is_approved,
    }

    return json.dumps(comments_json, ensure_ascii=False, indent=2)


class LaajEvaluator(Evaluator):
    def evaluate(self, review: CodeReviewEntry, pr: PullRequestEntry) -> LaajResult:

        prompt_template = PROMPT_FILE.read_text(encoding="utf-8")

        prompt = render_prompt_template(
            prompt_template,
            {
                "pull_request": _collect_pr_content(pr),
                "static_analysis_results": pr.static_analysis_results,
                "pull_request_comments": _collect_pr_comments(pr),
                "review_report": _collect_review_content(review.content),
            },
        )

        response = call_gemini(MODEL, prompt)
        result = from_dict(data_class=LaajResult, data=response)
        return result
