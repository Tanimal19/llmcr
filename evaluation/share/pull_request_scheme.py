from dataclasses import dataclass
from typing import List, Optional


@dataclass
class CommentEntry:
    poster: str
    created_at: str
    body: str
    file: Optional[str] = None
    lines: Optional[str] = None
    diff_content: Optional[str] = None


@dataclass
class ChangedFileEntry:
    path: str
    previous_path: Optional[str]
    patch: str
    content: str


@dataclass
class PullRequestEntry:
    id: int
    url: str
    title: str
    description: str
    is_closed: bool
    is_merged: bool
    is_approved: bool
    comments: List[CommentEntry]
    changed_files: List[ChangedFileEntry]
    changed_files_count: int = 0
    changed_content_size: int = 0

    rephrased_description: Optional[List[str]] = None
    rephrased_comments: Optional[List[str]] = None

    def __post_init__(self) -> None:
        if self.changed_files_count <= 0:
            self.changed_files_count = len(self.changed_files)

        if self.changed_content_size <= 0:
            self.changed_content_size = sum(len(f.content) for f in self.changed_files)
