import base64
import json
import os
import re
import time
from dataclasses import asdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, Iterator, List, Optional
import requests
from dotenv import load_dotenv
from scheme import ChangedFileEntry, CommentEntry, PullRequestEntry

REPO = "spring-projects/spring-ai"
MAX_PRS = 20
AFTER_RELEASE_RAG = "v2.0.0-M1"
BEFORE_DATE = "2026-05-01"
MIN_DESCRIPTION_WORDS = 30
MIN_COMMENTS = 5
OUTPUT_PATH = Path("exports") / "eval.jsonl"


def parse_iso_datetime(value: Optional[str]) -> Optional[datetime]:
    if not value:
        return None

    text = value.strip()
    if re.fullmatch(r"\d{4}-\d{2}-\d{2}", text):
        text = f"{text}T23:59:59Z"
    if text.endswith("Z"):
        text = f"{text[:-1]}+00:00"

    try:
        return datetime.fromisoformat(text).astimezone(timezone.utc)
    except ValueError:
        return None


def count_words(text: str) -> int:
    return len(re.findall(r"\b\w+\b", text))


class GitHubPRCollector:
    def __init__(
        self, owner_repo: str, token: Optional[str], sleep_seconds: float = 0.1
    ) -> None:
        self.owner_repo = owner_repo
        self.sleep_seconds = sleep_seconds
        self.base_api = f"https://api.github.com/repos/{owner_repo}"
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
            if self.sleep_seconds > 0:
                time.sleep(self.sleep_seconds)

        return results

    def get_release_date(self, release_tag: Optional[str]) -> Optional[str]:
        if not release_tag:
            return None

        release_url = f"{self.base_api}/releases/tags/{release_tag}"
        release = self.gh_get(release_url)
        if not isinstance(release, dict):
            return None
        return release.get("published_at") or release.get("created_at")

    @staticmethod
    def compute_is_approved(reviews: List[Dict]) -> bool:
        latest_state_by_user: Dict[str, str] = {}
        ordered_reviews = sorted(reviews, key=lambda r: r.get("submitted_at") or "")

        for review in ordered_reviews:
            user = (review.get("user") or {}).get("login")
            state = (review.get("state") or "").upper()
            if user:
                latest_state_by_user[user] = state

        return any(state == "APPROVED" for state in latest_state_by_user.values())

    @staticmethod
    def collect_comments(
        review_comments: List[Dict], reviews: List[Dict]
    ) -> List[CommentEntry]:
        comments: List[CommentEntry] = []

        for comment in review_comments:
            comments.append(
                CommentEntry(
                    type="comment",
                    poster=(comment.get("user") or {}).get("login"),
                    created_at=comment.get("created_at"),
                    body=comment.get("body") or "",
                )
            )

        for review in reviews:
            comments.append(
                CommentEntry(
                    type="event",
                    poster=(review.get("user") or {}).get("login"),
                    created_at=review.get("submitted_at"),
                    state=review.get("state"),
                    body=review.get("body") or "",
                )
            )

        comments.sort(key=lambda item: item.created_at or "")
        return comments

    def fetch_changed_files(
        self, pr_number: int, head_sha: Optional[str] = None
    ) -> List[ChangedFileEntry]:
        files: List[ChangedFileEntry] = []
        page = 1
        per_page = 50

        while True:
            params = {"page": page, "per_page": per_page}
            batch = self.gh_get(
                f"{self.base_api}/pulls/{pr_number}/files", params=params
            )

            if not isinstance(batch, list) or len(batch) == 0:
                break

            for file_info in batch:
                content = ""
                contents_url = file_info.get("contents_url")
                if contents_url:
                    try:
                        content_response = self.gh_get(
                            contents_url,
                            params={"ref": head_sha} if head_sha else None,
                        )
                        if isinstance(content_response, dict):
                            encoding = (content_response.get("encoding") or "").lower()
                            if encoding == "base64":
                                encoded = content_response.get("content") or ""
                                content = base64.b64decode(
                                    encoded.encode("utf-8")
                                ).decode("utf-8")
                            elif "content" in content_response:
                                content = content_response.get("content") or ""
                    except Exception:
                        content = ""

                files.append(
                    ChangedFileEntry(
                        path=file_info.get("filename") or "",
                        previous_path=file_info.get("previous_filename"),
                        patch=file_info.get("patch") or "",
                        content=content,
                    )
                )

            if len(batch) < per_page:
                break

            page += 1
            if self.sleep_seconds > 0:
                time.sleep(self.sleep_seconds)

        return files

    def get_first_commit_sha(self, pr_number: int) -> Optional[str]:
        commits = self.fetch_paginated(f"{self.base_api}/pulls/{pr_number}/commits")
        if not commits:
            return None

        first_commit = commits[0]
        if not isinstance(first_commit, dict):
            return None

        sha = first_commit.get("sha")
        return sha if isinstance(sha, str) and sha else None

    def fetch_commit_changed_files(self, commit_sha: str) -> List[ChangedFileEntry]:
        files: List[ChangedFileEntry] = []

        commit = self.gh_get(f"{self.base_api}/commits/{commit_sha}")
        if not isinstance(commit, dict):
            return files

        changed = commit.get("files")
        if not isinstance(changed, list):
            return files

        for file_info in changed:
            if not isinstance(file_info, dict):
                continue

            content = ""
            status = (file_info.get("status") or "").lower()
            contents_url = file_info.get("contents_url")

            if contents_url and status != "removed":
                try:
                    content_response = self.gh_get(
                        contents_url,
                        params={"ref": commit_sha},
                    )
                    if isinstance(content_response, dict):
                        encoding = (content_response.get("encoding") or "").lower()
                        if encoding == "base64":
                            encoded = content_response.get("content") or ""
                            content = base64.b64decode(encoded.encode("utf-8")).decode(
                                "utf-8"
                            )
                        elif "content" in content_response:
                            content = content_response.get("content") or ""
                except Exception:
                    content = ""

            files.append(
                ChangedFileEntry(
                    path=file_info.get("filename") or "",
                    previous_path=file_info.get("previous_filename"),
                    patch=file_info.get("patch") or "",
                    content=content,
                )
            )

        return files

    def build_pr_record(self, pr: Dict, review_comments) -> PullRequestEntry:
        pr_number = pr["number"]

        reviews = self.fetch_paginated(f"{self.base_api}/pulls/{pr_number}/reviews")
        first_commit_sha = self.get_first_commit_sha(pr_number)
        changed_files = (
            self.fetch_commit_changed_files(first_commit_sha)
            if first_commit_sha
            else []
        )

        return PullRequestEntry(
            pr_id=pr_number,
            url=pr.get("html_url"),
            title=pr.get("title") or "",
            pr_description=pr.get("body") or "",
            is_closed=(pr.get("state") or "").lower() == "closed",
            is_merged=bool(pr.get("merged")) or bool(pr.get("merged_at")),
            is_approved=self.compute_is_approved(reviews),
            comments=self.collect_comments(review_comments, reviews),
            changed_files=changed_files,
        )

    def fetch_pull_requests(
        self,
        max_prs: Optional[int] = None,
        since_date: Optional[str] = None,
        until_date: Optional[str] = None,
        min_description_words: int = 0,
        min_comments: int = 0,
    ) -> Iterator[PullRequestEntry]:
        params: Dict[str, object] = {
            "state": "all",
            "sort": "created",
            "direction": "desc",
        }
        page = 1
        per_page = 30
        yielded = 0
        since_datetime = parse_iso_datetime(since_date)
        until_datetime = parse_iso_datetime(until_date)

        while True:
            print(f"Fetching PRs page {page}.")

            q = dict(params)
            q.update({"page": page, "per_page": per_page})
            pulls = self.gh_get(f"{self.base_api}/pulls", params=q)

            if not isinstance(pulls, list) or len(pulls) == 0:
                break

            for pr in pulls:
                print(f"Processing PR #{pr.get('number')}: {pr.get('title')}")

                created_at = parse_iso_datetime(pr.get("created_at"))
                if created_at is None:
                    continue

                if until_datetime and created_at > until_datetime:
                    continue

                if since_datetime and created_at <= since_datetime:
                    return

                pr_description = pr.get("body") or ""
                if count_words(pr_description) < min_description_words:
                    print(
                        f"Insufficient description words: {count_words(pr_description)}"
                    )
                    continue

                review_comments = self.fetch_paginated(
                    f"{self.base_api}/pulls/{pr['number']}/comments"
                )
                if min_comments > 0 and len(review_comments) < min_comments:
                    print(f"Insufficient comments: {len(review_comments)}")
                    continue

                record = self.build_pr_record(pr, review_comments)

                yield record
                yielded += 1

                if max_prs is not None and yielded >= max_prs:
                    return

                if self.sleep_seconds > 0:
                    time.sleep(self.sleep_seconds)

            if len(pulls) < per_page:
                break

            page += 1


if __name__ == "__main__":
    project_root = Path(__file__).resolve().parents[1]
    load_dotenv(project_root / ".env")

    token = os.getenv("GITHUB_TOKEN")
    collector = GitHubPRCollector(owner_repo=REPO, token=token)
    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)

    since_date = None
    if AFTER_RELEASE_RAG:
        try:
            since_date = collector.get_release_date(AFTER_RELEASE_RAG)
        except Exception as exc:
            print(f"Warning: failed to fetch release {AFTER_RELEASE_RAG}: {exc}")

    print(f"Repo: {REPO}")
    if since_date:
        print(
            f"Filter lower bound: created after {since_date} (release: {AFTER_RELEASE_RAG})"
        )
    else:
        print("Filter lower bound: none")
    print(f"Filter upper bound: created on or before {BEFORE_DATE}")
    print(f"Filter minimum description words: {MIN_DESCRIPTION_WORDS}")
    print(f"Filter minimum comments: {MIN_COMMENTS}")
    print(f"Limit: newest {MAX_PRS} PRs")

    count = 0

    with OUTPUT_PATH.open("w", encoding="utf-8") as handle:
        for record in collector.fetch_pull_requests(
            max_prs=MAX_PRS,
            since_date=since_date,
            until_date=BEFORE_DATE,
            min_description_words=MIN_DESCRIPTION_WORDS,
            min_comments=MIN_COMMENTS,
        ):
            handle.write(json.dumps(asdict(record), ensure_ascii=False) + "\n")
            count += 1
            print(f"[{count}] Export PR {record.pr_id}: {record.title}")

    print(f"Exported: {count}")
    print(f"Output: {OUTPUT_PATH}")
