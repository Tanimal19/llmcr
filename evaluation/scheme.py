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
    changed_files_count: int = 0

    def __post_init__(self) -> None:
        if self.changed_files_count <= 0:
            self.changed_files_count = len(self.changed_files)


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


# ---------------------------------------------------------------------------
# Evaluation result structures
# ---------------------------------------------------------------------------


@dataclass
class TruthGrounding:
    hallucination_rate: float
    coverage_score: float
    mentioned_entities: float
    pr_entities: float
    mentioned_real_entities: float
    mentioned_pr_entities: float
    mentioned_entities_list: List[str] = field(default_factory=list)
    pr_entities_list: List[str] = field(default_factory=list)


@dataclass
class ReviewAlignment:
    comment_precision: float
    comment_recall: float
    comment_f1: float
    interpretation_precision: float
    interpretation_recall: float
    interpretation_f1: float
    comment_refs: List[str] = field(default_factory=list)
    comment_cands: List[str] = field(default_factory=list)
    interp_refs: List[str] = field(default_factory=list)
    interp_cands: List[str] = field(default_factory=list)


@dataclass
class QualityScore:
    comprehensiveness: int
    conciseness: int
    relevance: int
    topics_to_be_covered: List[str]
    step_by_step_analysis: List[str]
    rationale: str


@dataclass
class EvaluationMeta:
    sentence_count: int
    checklist_item_count: int
    issue_count: int
    review: ParsedReview
    pull_request: PullRequestEntry


@dataclass
class EvaluationResult:
    pr_id: int
    truth_grounding: TruthGrounding
    review_alignment: ReviewAlignment
    quality_score: QualityScore
    repetitive_rate: float
    meta: EvaluationMeta
