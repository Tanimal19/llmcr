import re
from dataclasses import dataclass
from typing import List

from pre_eval.bot_review_collect.scheme import BotCommentEntry, BotReviewEntry


@dataclass(frozen=True)
class CopilotChangeEntry:
    file_path: str
    description: str


@dataclass(frozen=True)
class CopilotIssueEntry:
    file_path: str
    lines: str
    content: str


@dataclass(frozen=True)
class CopilotReviewEntry:
    pr_id: int
    overview: str
    changes: List[CopilotChangeEntry]
    issues: List[CopilotIssueEntry]


_OVERVIEW_SECTION_RE = re.compile(
    r"##\s*Pull request overview\s*(.*?)(?:\n\s*\*\*Changes:\*\*|\n\s*###\s*Reviewed changes\s*|\Z)",
    re.DOTALL,
)
_CHANGES_SECTION_RE = re.compile(
    r"\*\*Changes:\*\*\s*(.*?)(?:\n\s*###\s*Reviewed changes\s*|\Z)",
    re.DOTALL,
)
_BULLET_RE = re.compile(r"^\s*-\s+(.+?)\s*$", re.MULTILINE)
_REVIEWED_CHANGES_RE = re.compile(
    r"###\s*Reviewed changes\s*(.*?)(?:\n\s*---\s*|\Z)",
    re.DOTALL,
)
_TABLE_ROW_RE = re.compile(
    r"^\|\s*(?P<file>.+?)\s*\|\s*(?P<description>.+?)\s*\|\s*$",
    re.MULTILINE,
)


def parse_copilot_review(review: BotReviewEntry) -> CopilotReviewEntry:
    overview = ""
    changes: List[CopilotChangeEntry] = []
    issues: List[CopilotIssueEntry] = []

    for comment in review.comments:
        if comment.type == "review_summary" and not overview:
            overview = _extract_overview(comment.body)
            changes = _extract_changes(comment.body)

        if comment.type == "review_comment":
            issues.append(_parse_issue_comment(comment))

    return CopilotReviewEntry(
        pr_id=int(review.pr_id),
        overview=overview,
        changes=changes,
        issues=issues,
    )


def _parse_issue_comment(comment: BotCommentEntry) -> CopilotIssueEntry:
    content = _strip_markdown(comment.body)

    return CopilotIssueEntry(
        file_path=comment.file or "",
        lines=comment.lines or "",
        content=content,
    )


def _extract_overview(body: str) -> str:
    match = _OVERVIEW_SECTION_RE.search(body)
    if not match:
        return ""

    return _strip_markdown(match.group(1))


def _extract_changes(body: str) -> List[CopilotChangeEntry]:
    reviewed_changes = _extract_reviewed_changes(body)
    if reviewed_changes:
        return reviewed_changes

    section_match = _CHANGES_SECTION_RE.search(body)
    if not section_match:
        return []

    changes: List[CopilotChangeEntry] = []
    for bullet_match in _BULLET_RE.finditer(section_match.group(1)):
        changes.append(
            CopilotChangeEntry(
                file_path="", description=_strip_markdown(bullet_match.group(1))
            )
        )

    return changes


def _extract_reviewed_changes(body: str) -> List[CopilotChangeEntry]:
    section_match = _REVIEWED_CHANGES_RE.search(body)
    if not section_match:
        return []

    section = section_match.group(1)
    changes: List[CopilotChangeEntry] = []
    for row in _TABLE_ROW_RE.finditer(section):
        file_path = _strip_markdown(row.group("file"))
        description = _strip_markdown(row.group("description"))

        normalized_file = file_path.lower().strip()
        if normalized_file == "file" or set(normalized_file) <= {"-", ":", " "}:
            continue

        if set(description.strip()) <= {"-", ":", " "}:
            continue

        changes.append(CopilotChangeEntry(file_path=file_path, description=description))

    return changes


def _strip_markdown(text: str) -> str:
    text = re.sub(r"```.*?```", " ", text, flags=re.DOTALL)
    text = re.sub(r"\[(.*?)\]\((.*?)\)", r"\1", text)
    text = re.sub(r"<[^>]+>", " ", text)
    text = text.replace("`", "")
    text = re.sub(r"\*\*|__", "", text)
    text = re.sub(r"\s+", " ", text)
    return text.strip()
