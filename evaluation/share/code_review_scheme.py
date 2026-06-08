from dataclasses import dataclass, field
from typing import List, Optional
from dacite import from_dict
from pre_eval.bot_review_collect.converter.coderabbit import CoderabbitReviewEntry
from pre_eval.bot_review_collect.converter.copilot import CopilotReviewEntry


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
class CodeReviewContent:
    motivation: str = ""
    good_points: List[str] = field(default_factory=list)
    bad_points: List[str] = field(default_factory=list)
    suggestion: str = ""
    implementation_details: List[ImplementationDetails] = field(default_factory=list)
    issues: List[Issue] = field(default_factory=list)
    issue_count: int = 0
    content_size: int = 0

    def __post_init__(self) -> None:
        if self.issue_count <= 0:
            self.issue_count = len(self.issues)

        if self.content_size <= 0:
            self.content_size = (
                sum(
                    len(field)
                    for field in [
                        self.motivation,
                        self.suggestion,
                    ]
                )
                + sum(len(point) for point in self.good_points)
                + sum(len(point) for point in self.bad_points)
                + sum(
                    len(issue.title) + len(issue.location) + len(issue.detail)
                    for issue in self.issues
                )
                + sum(
                    len(impl.filename) + sum(len(detail) for detail in impl.details)
                    for impl in self.implementation_details
                )
            )


@dataclass
class CodeReviewEntry:
    pr_id: int
    pr_title: str
    content: CodeReviewContent = field(default_factory=CodeReviewContent)

    @staticmethod
    def from_llmcr_format(data: dict) -> "CodeReviewEntry":
        return from_dict(CodeReviewEntry, data)

    @staticmethod
    def from_coderabbit_format(data: dict) -> "CodeReviewEntry":
        entry = from_dict(CoderabbitReviewEntry, data)
        return CodeReviewEntry(
            pr_id=entry.pr_id,
            pr_title="",
            content=CodeReviewContent(
                motivation=entry.walkthrough,
                implementation_details=[
                    ImplementationDetails(
                        filename=change.layer, details=[change.summary]
                    )
                    for change in entry.changes
                ],
                issues=[
                    Issue(
                        issue_type=issue.type,
                        title="",
                        location=f"{issue.file_path}:{issue.lines}",
                        detail=issue.content,
                    )
                    for issue in entry.issues
                ],
            ),
        )

    @staticmethod
    def from_copilot_format(data: dict) -> "CodeReviewEntry":
        entry = from_dict(CopilotReviewEntry, data)
        return CodeReviewEntry(
            pr_id=entry.pr_id,
            pr_title="",
            content=CodeReviewContent(
                motivation=entry.overview,
                implementation_details=[
                    ImplementationDetails(
                        filename=change.file_path, details=[change.description]
                    )
                    for change in entry.changes
                ],
                issues=[
                    Issue(
                        issue_type="",
                        title="",
                        location=f"{issue.file_path}:{issue.lines}",
                        detail=issue.content,
                    )
                    for issue in entry.issues
                ],
            ),
        )
