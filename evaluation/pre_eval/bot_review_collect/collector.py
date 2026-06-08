import os
import re
import csv
import requests
from dataclasses import asdict
from collections import defaultdict
from pathlib import Path
from config import BOT_REVIEW_PR_URLS_CSV, MERGED_REVIEW_JSONL
from typing import Any, Dict, List, Optional
from pre_eval.bot_review_collect.scheme import (
    PullRequestRef,
    BotCommentEntry,
    BotReviewEntry,
)
from pre_eval.bot_review_collect.converter.copilot import parse_copilot_review
from pre_eval.bot_review_collect.converter.coderabbit import parse_coderabbit_review
from share.utils import append_jsonl_entry

PR_URL_PATTERN = re.compile(r"^https://github\.com/([^/]+)/([^/]+)/pull/(\d+)(?:/.*)?$")
CONVERTER_FUNCTIONS = {
    "coderabbit": parse_coderabbit_review,
    "copilot": parse_copilot_review,
}


def _parse_pr_ref(url: str, original_pr_id: str) -> PullRequestRef:
    match = PR_URL_PATTERN.match(url.strip())
    if not match:
        raise ValueError(f"Invalid PR URL: {url}")

    owner, repo, number = match.groups()
    return PullRequestRef(
        owner=owner,
        repo=repo,
        number=int(number),
        url=url.strip(),
        original_pr_id=original_pr_id,
    )


def _load_pr_refs(urls_file: Path) -> List[PullRequestRef]:
    rows: List[PullRequestRef] = []

    if not urls_file.exists():
        raise FileNotFoundError(f"PR URL file does not exist: {urls_file}")

    with urls_file.open(encoding="utf-8", newline="") as csv_file:
        reader = csv.DictReader(csv_file)
        if reader.fieldnames and {
            "reproduced_url",
            "original_pr_id",
        }.issubset(reader.fieldnames):
            for row in reader:
                reproduced_url = str(row.get("reproduced_url") or "").strip()
                original_pr_id = str(row.get("original_pr_id") or "").strip()
                if not reproduced_url or reproduced_url.startswith("#"):
                    continue
                if not original_pr_id:
                    raise ValueError(
                        f"Missing original_pr_id for URL: {reproduced_url}"
                    )
                rows.append(
                    _parse_pr_ref(reproduced_url, original_pr_id=original_pr_id)
                )

    if not rows:
        raise ValueError("No PR URLs provided.")
    return rows


class GitHubCommentCollector:
    def __init__(self, token: Optional[str]) -> None:
        self.session = requests.Session()
        self.session.headers.update(
            {
                "Accept": "application/vnd.github+json",
                "X-GitHub-Api-Version": "2022-11-28",
            }
        )
        if token:
            self.session.headers["Authorization"] = f"Bearer {token}"

    def gh_get(
        self,
        url: str,
        params: Optional[Dict] = None,
        accept: Optional[str] = None,
        raw_text: bool = False,
    ):
        headers = {"Accept": accept} if accept else None
        resp = self.session.get(url, params=params, headers=headers, timeout=30)

        if resp.status_code == 403 and "rate limit" in resp.text.lower():
            reset_at = resp.headers.get("X-RateLimit-Reset")
            raise RuntimeError(f"Rate limit exceeded. X-RateLimit-Reset={reset_at}")

        resp.raise_for_status()
        if raw_text:
            return resp.text
        return resp.json()

    def fetch_paginated(
        self, url: str, params: Optional[Dict] = None, max_items: Optional[int] = None
    ) -> List[Dict]:
        results: List[Dict] = []
        page = 1
        per_page = 100

        while True:
            q = dict(params or {})
            q.update({"page": page, "per_page": per_page})
            batch = self.gh_get(url, params=q)

            if not isinstance(batch, list) or len(batch) == 0:
                break

            for item in batch:
                results.append(item)
                if max_items is not None and len(results) >= max_items:
                    return results

            if len(batch) < per_page:
                break

            page += 1

        return results

    @staticmethod
    def is_bot_user(user: Dict[str, Any]) -> bool:
        login = str(user.get("login") or "")
        user_type = str(user.get("type") or "")
        return user_type.lower() == "bot" or login.endswith("[bot]")

    @staticmethod
    def normalize_bot_name(bot_name: str) -> str:
        normalized = bot_name.strip().lower()

        if "coderabbit" in normalized:
            return "coderabbit"
        if "copilot" in normalized:
            return "copilot"

        return "unknown"

    @staticmethod
    def format_lines(comment: Dict) -> Optional[str]:
        start_line = comment.get("start_line")
        line = comment.get("line")
        original_start_line = comment.get("original_start_line")
        original_line = comment.get("original_line")

        if isinstance(start_line, int) and isinstance(line, int):
            return f"{start_line}-{line}" if start_line != line else str(line)

        if isinstance(original_start_line, int) and isinstance(original_line, int):
            if original_start_line != original_line:
                return f"{original_start_line}-{original_line}"
            return str(original_line)

        if isinstance(line, int):
            return str(line)

        if isinstance(original_line, int):
            return str(original_line)

        return None

    @staticmethod
    def normalize_comment(
        type: str,
        comment: Dict[str, Any],
    ) -> BotCommentEntry:
        return BotCommentEntry(
            type=type,
            poster=GitHubCommentCollector.normalize_bot_name(
                str((comment.get("user") or {}).get("login"))
            ),
            body=str(comment.get("body") or "").strip(),
            file=comment.get("path") or None,
            lines=GitHubCommentCollector.format_lines(comment),
            diff_content=comment.get("diff_hunk") or None,
        )

    def fetch_pr_bot_comments(self, pr: PullRequestRef) -> List[BotReviewEntry]:

        base = f"https://api.github.com/repos/{pr.owner}/{pr.repo}"
        issue_comments = self.fetch_paginated(f"{base}/issues/{pr.number}/comments")
        review_comments = self.fetch_paginated(f"{base}/pulls/{pr.number}/comments")
        reviews = self.fetch_paginated(f"{base}/pulls/{pr.number}/reviews")

        grouped: Dict[str, List[BotCommentEntry]] = defaultdict(list)

        for raw in issue_comments:
            user = raw.get("user") or {}
            if not self.is_bot_user(user):
                continue
            entry = self.normalize_comment("issue_comment", raw)
            grouped[str(entry.poster)].append(entry)

        for raw in review_comments:
            user = raw.get("user") or {}
            if not self.is_bot_user(user):
                continue
            entry = self.normalize_comment("review_comment", raw)
            grouped[str(entry.poster)].append(entry)

        for raw in reviews:
            body = (raw.get("body") or "").strip()
            user = raw.get("user") or {}
            if not body or not self.is_bot_user(user):
                continue
            entry = self.normalize_comment("review_summary", raw)
            grouped[str(entry.poster)].append(entry)

        entries = []
        for bot, comments in grouped.items():
            print(f"  Found {len(comments)} comment(s) from bot '{bot}'")
            entries.append(
                BotReviewEntry(
                    pr_id=pr.original_pr_id,
                    bot_name=bot,
                    comments=comments,
                )
            )

        return entries


def main() -> None:
    pr_refs = _load_pr_refs(BOT_REVIEW_PR_URLS_CSV)

    token = os.getenv("GITHUB_TOKEN")
    client = GitHubCommentCollector(token=token)

    for pr in pr_refs:
        print(f"Processing PR: {pr.url} (original_pr_id={pr.original_pr_id})")
        try:
            entries = client.fetch_pr_bot_comments(pr)

            for entry in entries:
                converter = CONVERTER_FUNCTIONS.get(entry.bot_name)
                if converter:
                    print(f"  Converting review data for bot '{entry.bot_name}'...")
                    converted = converter(entry)
                    output_path = MERGED_REVIEW_JSONL(entry.bot_name)
                    append_jsonl_entry(
                        output_path.open("a", encoding="utf-8"),
                        asdict(converted),
                    )
                else:
                    print(entry)
                    continue

        except Exception as e:
            print(f"  Error processing PR {pr.url}: {e}")


if __name__ == "__main__":
    main()
