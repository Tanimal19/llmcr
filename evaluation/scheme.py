from dataclasses import dataclass
from typing import List, Optional


@dataclass
class CommentEntry:
    type: str
    poster: Optional[str]
    created_at: Optional[str]
    body: str
    state: Optional[str] = None


@dataclass
class ChangedFileEntry:
    path: str
    previous_path: Optional[str]
    patch: str
    content: str


@dataclass
class PullRequestEntry:
    pr_id: int
    url: Optional[str]
    title: str
    pr_description: str
    is_closed: bool
    is_merged: bool
    is_approved: bool
    comments: List[CommentEntry]
    changed_files: List[ChangedFileEntry]


@dataclass
class PreEvaluatedPullRequestEntry(PullRequestEntry):
    normalized_review_sentences: List[str]
    normalized_description_sentences: List[str]
