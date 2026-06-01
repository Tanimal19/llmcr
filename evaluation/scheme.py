from dataclasses import dataclass, field
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


@dataclass
class ChecklistEvidence:
    filepath: str
    lines: str
    reason: str


@dataclass
class ChecklistItem:
    title: str
    final_answer: str
    analysis: str
    evidences: List[ChecklistEvidence] = field(default_factory=list)
    final_answer_labeled: bool = False
    analysis_labeled: bool = False
    expected_evidence_count: int = 0


@dataclass
class Issue:
    issue_type: str
    title: str
    location: str
    detail: str


@dataclass
class ParsedReview:
    motivation: str = ""
    good_points: str = ""
    bad_points: str = ""
    suggestion: str = ""
    implementation_details: str = ""
    issues: List[Issue] = field(default_factory=list)
    static_analysis_results: str = ""
    change_description: str = ""
    change_motivation: str = ""
    checklist_items: List[ChecklistItem] = field(default_factory=list)
