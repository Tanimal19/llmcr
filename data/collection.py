import argparse
import base64
import json
import os
import time
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Dict, Iterator, List, Optional

import requests
from dotenv import load_dotenv


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
    url: Optional[str]
    title: str
    pr_description: str
    is_closed: bool
    is_merged: bool
    is_approved: bool
    comments: List[CommentEntry]
    changed_files: List[ChangedFileEntry]


class GitHubPRExporter:
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
        issue_comments: List[Dict], review_comments: List[Dict], reviews: List[Dict]
    ) -> List[CommentEntry]:
        comments: List[CommentEntry] = []

        for comment in issue_comments:
            comments.append(
                CommentEntry(
                    type="issue_comment",
                    poster=(comment.get("user") or {}).get("login"),
                    created_at=comment.get("created_at"),
                    body=comment.get("body") or "",
                )
            )

        for comment in review_comments:
            comments.append(
                CommentEntry(
                    type="review_comment",
                    poster=(comment.get("user") or {}).get("login"),
                    created_at=comment.get("created_at"),
                    body=comment.get("body") or "",
                )
            )

        for review in reviews:
            comments.append(
                CommentEntry(
                    type="review_event",
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

    def build_pr_record(self, pr: Dict) -> PullRequestEntry:
        pr_number = pr["number"]

        issue_comments = self.fetch_paginated(
            f"{self.base_api}/issues/{pr_number}/comments"
        )
        review_comments = self.fetch_paginated(
            f"{self.base_api}/pulls/{pr_number}/comments"
        )
        reviews = self.fetch_paginated(f"{self.base_api}/pulls/{pr_number}/reviews")
        changed_files = self.fetch_changed_files(
            pr_number, head_sha=(pr.get("head") or {}).get("sha")
        )

        return PullRequestEntry(
            url=pr.get("html_url"),
            title=pr.get("title") or "",
            pr_description=pr.get("body") or "",
            is_closed=(pr.get("state") or "").lower() == "closed",
            is_merged=bool(pr.get("merged")) or bool(pr.get("merged_at")),
            is_approved=self.compute_is_approved(reviews),
            comments=self.collect_comments(issue_comments, review_comments, reviews),
            changed_files=changed_files,
        )

    def fetch_pull_requests(
        self,
        max_prs: Optional[int] = None,
        since_date: Optional[str] = None,
    ) -> Iterator[PullRequestEntry]:
        params: Dict[str, object] = {
            "state": "all",
            "sort": "created",
            "direction": "desc",
        }
        page = 1
        per_page = 30
        yielded = 0

        while True:
            print(f"Fetching PRs page {page}.")

            q = dict(params)
            q.update({"page": page, "per_page": per_page})
            pulls = self.gh_get(f"{self.base_api}/pulls", params=q)

            if not isinstance(pulls, list) or len(pulls) == 0:
                break

            for pr in pulls:
                if since_date and (pr.get("created_at") or "") <= since_date:
                    return

                yield self.build_pr_record(pr)
                yielded += 1

                if max_prs is not None and yielded >= max_prs:
                    return

                if self.sleep_seconds > 0:
                    time.sleep(self.sleep_seconds)

            if len(pulls) < per_page:
                break

            page += 1


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Export GitHub pull requests to JSONL")
    parser.add_argument(
        "--repo", default="spring-projects/spring-ai", help="GitHub owner/repo"
    )
    parser.add_argument(
        "--max-prs", type=int, default=None, help="Maximum number of PRs to export"
    )
    parser.add_argument(
        "--release-tag",
        default="v2.0.0-M1",
        help="Only export PRs created after this release tag; set empty string to disable",
    )
    parser.add_argument("--output", default=None, help="Output JSONL path")
    return parser.parse_args()


if __name__ == "__main__":
    project_root = Path(__file__).resolve().parents[1]
    load_dotenv(project_root / ".env")

    args = parse_args()
    token = os.getenv("GITHUB_TOKEN")

    exporter = GitHubPRExporter(owner_repo=args.repo, token=token)
    repo_name = args.repo.split("/", 1)[1]

    output_path = (
        Path(args.output) if args.output else Path("exports") / f"{repo_name}-prs.jsonl"
    )
    output_path.parent.mkdir(parents=True, exist_ok=True)

    release_tag = args.release_tag.strip() if args.release_tag is not None else ""
    since_date = None
    if release_tag:
        try:
            since_date = exporter.get_release_date(release_tag)
        except Exception as exc:
            print(f"Warning: failed to fetch release {release_tag}: {exc}")

    print(f"Repo: {args.repo}")
    if since_date:
        print(f"Filter: PRs created after {since_date} (release: {release_tag})")
    else:
        print("Filter: none")

    write_mode = "a" if output_path.exists() else "w"
    count = 0

    with output_path.open(write_mode, encoding="utf-8") as handle:
        for record in exporter.fetch_pull_requests(
            max_prs=args.max_prs, since_date=since_date
        ):
            handle.write(json.dumps(asdict(record), ensure_ascii=False) + "\n")
            count += 1
            print(f"[{count}] Export PR: {record.title}")

    print(f"Exported: {count}")
    print(f"Output: {output_path}")
