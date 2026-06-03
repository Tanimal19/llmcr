from abc import ABC, abstractmethod
from typing import Any
from share.code_review_scheme import CodeReviewEntry
from share.pull_request_scheme import PullRequestEntry


class Evaluator(ABC):
    @abstractmethod
    def evaluate(self, review: CodeReviewEntry, pr: PullRequestEntry) -> Any:
        pass
