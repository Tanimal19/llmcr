from dataclasses import dataclass
from typing import Optional


@dataclass(frozen=True)
class PullRequestRef:
    owner: str
    repo: str
    number: int
    url: str
    original_pr_id: str


@dataclass(frozen=True)
class BotCommentEntry:
    type: str
    poster: str
    body: str
    file: Optional[str] = None
    lines: Optional[str] = None
    diff_content: Optional[str] = None


@dataclass(frozen=True)
class BotReviewEntry:
    pr_id: str
    bot_name: str
    comments: list[BotCommentEntry]
