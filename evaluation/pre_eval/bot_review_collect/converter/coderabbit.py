import re
from dataclasses import dataclass
from typing import List
from pre_eval.bot_review_collect.scheme import BotReviewEntry, BotCommentEntry


@dataclass(frozen=True)
class CoderabbitChangeEntry:
    layer: str
    summary: str


@dataclass(frozen=True)
class CoderabbitIssueEntry:
    file_path: str
    lines: str
    type: str
    severity: str
    content: str


@dataclass(frozen=True)
class CoderabbitReviewEntry:
    pr_id: int
    walkthrough: str
    changes: List[CoderabbitChangeEntry]
    issues: List[CoderabbitIssueEntry]


_ISSUE_HEADER_RE = re.compile(r"^_(.*?)_\s*\|\s*_(.*?)_\s*\|\s*_(.*?)_", re.MULTILINE)
_BOLD_LINE_RE = re.compile(r"\*\*(.+?)\*\*")
_WALKTHROUGH_BLOCK_RE = re.compile(
    r"<!--\s*walkthrough_start\s*-->(.*?)<!--\s*walkthrough_end\s*-->",
    re.DOTALL,
)
_WALKTHROUGH_SECTION_RE = re.compile(
    r"##\s*Walkthrough\s*(.*?)(?:##\s*Changes\s*|\Z)", re.DOTALL
)
_CHANGES_SECTION_RE = re.compile(r"##\s*Changes\s*(.*?)(?:##\s|\Z)", re.DOTALL)
_CHANGE_ROW_RE = re.compile(
    r"^\|\s*\*\*(?P<layer>.+?)\*\*.*?\|\s*(?P<summary>.+?)\s*\|\s*$",
    re.MULTILINE,
)


def parse_coderabbit_review(review: BotReviewEntry) -> CoderabbitReviewEntry:
    walkthrough = ""
    changes: List[CoderabbitChangeEntry] = []
    issues: List[CoderabbitIssueEntry] = []

    for comment in review.comments:
        if comment.type == "issue_comment" and not walkthrough:
            walkthrough_block = _extract_walkthrough(comment.body)
            walkthrough = _extract_walkthrough_only(walkthrough_block)
            changes = _extract_changes(walkthrough_block)

        if comment.type == "review_comment":
            issues.append(_parse_issue_comment(comment))

    return CoderabbitReviewEntry(
        pr_id=int(review.pr_id), walkthrough=walkthrough, changes=changes, issues=issues
    )


def _parse_issue_comment(comment: BotCommentEntry) -> CoderabbitIssueEntry:
    issue_type, severity = _parse_issue_header(comment.body)
    content = _parse_issue_content(comment.body)

    return CoderabbitIssueEntry(
        file_path=comment.file or "",
        lines=comment.lines or "",
        type=issue_type,
        severity=severity,
        content=content,
    )


def _parse_issue_header(body: str) -> tuple[str, str]:
    match = _ISSUE_HEADER_RE.search(body)
    if not match:
        return "Unknown", "Unknown"

    issue_type = _normalize_label(match.group(1))
    severity = _normalize_label(match.group(2))
    return issue_type, severity


def _parse_issue_content(body: str) -> str:
    bold_match = _BOLD_LINE_RE.search(body)

    if bold_match:
        title = _strip_markdown(bold_match.group(1).strip())
        tail = body[bold_match.end() :]
        detail = _strip_markdown(re.split(r"\n<details>\s*", tail, maxsplit=1)[0])
        if detail:
            return f"{title} {detail}".strip()
        return title

    details_split = re.split(r"\n<details>\s*", body, maxsplit=1)
    top_block = details_split[0]
    fallback = _strip_markdown(top_block)
    if fallback:
        return fallback

    first_line = body.strip().splitlines()[0] if body.strip() else ""
    return _strip_markdown(first_line)


def _extract_walkthrough(body: str) -> str:
    match = _WALKTHROUGH_BLOCK_RE.search(body)
    if match:
        return match.group(1).strip()

    return body.strip()


def _extract_walkthrough_only(walkthrough_block: str) -> str:
    section_match = _WALKTHROUGH_SECTION_RE.search(walkthrough_block)
    if not section_match:
        return ""

    return _strip_markdown(section_match.group(1)).strip()


def _extract_changes(walkthrough: str) -> List[CoderabbitChangeEntry]:
    section_match = _CHANGES_SECTION_RE.search(walkthrough)
    if not section_match:
        return []

    section = section_match.group(1)
    changes: List[CoderabbitChangeEntry] = []
    for row in _CHANGE_ROW_RE.finditer(section):
        layer = _strip_markdown(row.group("layer")).strip()
        summary = _strip_markdown(row.group("summary")).strip()
        changes.append(CoderabbitChangeEntry(layer=layer, summary=summary))

    return changes


def _normalize_label(raw: str) -> str:
    text = _strip_markdown(raw)
    text = re.sub(r"^[^A-Za-z]+", "", text)
    return text.strip() or "Unknown"


def _strip_markdown(text: str) -> str:
    text = text.replace("`", "")
    text = re.sub(r"<[^>]+>", " ", text)
    text = re.sub(r"\s+", " ", text)
    return text.strip()
