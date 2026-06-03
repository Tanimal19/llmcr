from dataclasses import dataclass, field
from typing import List, Optional
from dacite import from_dict


@dataclass
class ImplementationDetails:
    filename: str = ""
    details: List[str] = field(default_factory=list)


@dataclass
class Issue:
    issue_type: str = ""
    title: str = ""
    location: str = ""
    detail: str = ""


@dataclass
class Interpretation:
    change_description: str = ""
    change_motivation: str = ""


@dataclass
class ChecklistEvidenceItem:
    filepath: str = ""
    lines: str = ""
    reason: str = ""


@dataclass
class ChecklistItem:
    title: str = ""
    final_answer: str = ""
    analysis: str = ""
    evidences: List[ChecklistEvidenceItem] = field(default_factory=list)


@dataclass
class CodeReviewEntry:
    pr_id: int
    pr_title: str
    motivation: str = ""
    good_points: str = ""
    bad_points: str = ""
    suggestion: str = ""
    implementation_details: List[ImplementationDetails] = field(default_factory=list)
    issues: List[Issue] = field(default_factory=list)
    issue_count: int = 0
    content_size: int = 0

    # belowing fields are data used during the review but not counted as the review content itself
    static_analysis_results: str = ""
    interpretation: Optional[Interpretation] = field(default_factory=Interpretation)
    checklist_items: Optional[List[ChecklistItem]] = field(default_factory=list)
    checklist_item_count: int = 0

    def __post_init__(self) -> None:
        if self.issue_count <= 0:
            self.issue_count = len(self.issues)

        if self.content_size <= 0:
            self.content_size = (
                sum(
                    len(field)
                    for field in [
                        self.motivation,
                        self.good_points,
                        self.bad_points,
                        self.suggestion,
                    ]
                )
                + sum(
                    len(issue.title) + len(issue.location) + len(issue.detail)
                    for issue in self.issues
                )
                + sum(
                    len(impl.filename) + sum(len(detail) for detail in impl.details)
                    for impl in self.implementation_details
                )
            )

        if self.checklist_item_count <= 0 and self.checklist_items is not None:
            self.checklist_item_count = len(self.checklist_items)

    @staticmethod
    def from_llmcr_review(data: dict) -> "CodeReviewEntry":
        # we can use dacite to parse the nested structure, as the LLMCR output review are exactly match the dataclass
        return from_dict(data_class=CodeReviewEntry, data=data)

    @staticmethod
    def from_coderabbit_review(data: dict) -> "CodeReviewEntry":
        raise NotImplementedError("Coderabbit format parsing is not implemented yet")

    @staticmethod
    def from_copilot_review(data: dict) -> "CodeReviewEntry":
        raise NotImplementedError("Copilot format parsing is not implemented yet")
