import base64, json, os, subprocess, time
from pathlib import Path
from urllib.request import Request, urlopen
from urllib.error import HTTPError

ROOT = Path(__file__).parent.parent
DATASETS = ROOT / "datasets"
TOKEN = os.environ.get("GITHUB_TOKEN", "")

ISSUE_LIMIT = 10000  # max issues to download
PR_LIMIT = 10000  # max PRs to download


def _gh_headers():
    h = {"Accept": "application/vnd.github+json"}
    if TOKEN:
        h["Authorization"] = f"Bearer {TOKEN}"
    return h


def gh_get(url: str) -> list | dict:
    try:
        with urlopen(Request(url, headers=_gh_headers())) as r:
            return json.loads(r.read())
    except HTTPError as e:
        print(f"  {e}: {url}")
        return []


def gh_iter(base_url: str):
    """Lazy paginator — stops fetching as soon as caller breaks."""
    sep = "&" if "?" in base_url else "?"
    page = 1
    while True:
        items = gh_get(f"{base_url}{sep}per_page=100&page={page}")
        if not items:
            break
        yield from items
        page += 1
        time.sleep(0.2)


def gh_list(base_url: str) -> list:
    """Eager paginator — fetches all pages. Use for sub-resources (comments, files)."""
    return list(gh_iter(base_url))


def _pr_result(pr: dict) -> str:
    if pr.get("merged_at"):
        return "merged"
    if pr.get("state") == "closed":
        return "closed"
    return "opened"


def _format_comment(c: dict, path: str | None = None, line: int | None = None) -> dict:
    loc = f"{path}:{line}" if path and line else None
    return {
        "content": c.get("body", ""),
        "date": c.get("created_at", ""),
        "mentioned_location": loc,
    }


def _fetch_file_content(repo: str, filepath: str, ref: str) -> str:
    url = f"https://api.github.com/repos/{repo}/contents/{filepath}?ref={ref}"
    data = gh_get(url)
    if isinstance(data, dict) and data.get("encoding") == "base64":
        return base64.b64decode(data["content"]).decode("utf-8", errors="replace")
    return ""


def download_issues(repo: str, dest: Path):
    dest.mkdir(parents=True, exist_ok=True)
    count = 0
    for issue in gh_iter(f"https://api.github.com/repos/{repo}/issues?state=all"):
        if count >= ISSUE_LIMIT:
            print(f"  reached limit ({ISSUE_LIMIT})")
            break
        if "pull_request" in issue:
            continue
        n = issue["number"]
        out = dest / f"{n}.json"
        if out.exists():
            print(f"  skip (exists): issue #{n}")
            continue
        raw_comments = gh_list(
            f"https://api.github.com/repos/{repo}/issues/{n}/comments"
        )
        doc = {
            "id": n,
            "title": issue.get("title", ""),
            "date": issue.get("created_at", ""),
            "description": issue.get("body", ""),
            "closed": issue.get("state") == "closed",
            "comments": [_format_comment(c) for c in raw_comments],
        }
        out.write_text(json.dumps(doc, indent=2, ensure_ascii=False))
        count += 1
        print(f"  issue #{n} ({len(raw_comments)} comments)")
        time.sleep(0.3)
    print(f"  done: {count} new issues in {dest.name}/")


def download_pulls(repo: str, dest: Path):
    dest.mkdir(parents=True, exist_ok=True)
    count = 0
    for pr in gh_iter(f"https://api.github.com/repos/{repo}/pulls?state=all"):
        if count >= PR_LIMIT:
            print(f"  reached limit ({PR_LIMIT})")
            break
        n = pr["number"]
        out = dest / f"{n}.json"
        if out.exists():
            print(f"  skip (exists): PR #{n}")
            continue

        base_sha = pr.get("base", {}).get("sha", "")

        # inline review comments (have path + line)
        review_comments = gh_list(
            f"https://api.github.com/repos/{repo}/pulls/{n}/comments"
        )
        # general discussion comments (no location)
        issue_comments = gh_list(
            f"https://api.github.com/repos/{repo}/issues/{n}/comments"
        )

        all_comments = [
            _format_comment(c, c.get("path"), c.get("line") or c.get("original_line"))
            for c in review_comments
        ] + [_format_comment(c) for c in issue_comments]

        raw_files = gh_list(f"https://api.github.com/repos/{repo}/pulls/{n}/files")
        changed_files = []
        for f in raw_files:
            original = (
                _fetch_file_content(repo, f["filename"], base_sha)
                if f.get("status") != "added"
                else ""
            )
            changed_files.append(
                {
                    "original_filepath": f.get("previous_filename")
                    or f.get("filename", ""),
                    "new_filepath": f.get("filename", ""),
                    "original_content": original,
                    "patch": f.get("patch", ""),
                }
            )
            time.sleep(0.2)

        doc = {
            "id": n,
            "title": pr.get("title", ""),
            "date": pr.get("created_at", ""),
            "description": pr.get("body", ""),
            "result": _pr_result(pr),
            "comments": all_comments,
            "changed_files": changed_files,
        }
        out.write_text(json.dumps(doc, indent=2, ensure_ascii=False))
        count += 1
        print(
            f"  PR #{n} ({len(raw_files)} files, {len(all_comments)} comments) [{doc['result']}]"
        )
        time.sleep(0.5)
    print(f"  done: {count} new PRs in {dest.name}/")


def clone(url: str, dest: Path):
    if dest.exists():
        print(f"  skip (exists): {dest.name}")
        return
    subprocess.run(["git", "clone", "--depth=1", url, str(dest)], check=True)
    print(f"  done: {dest.name}")


def sparse_clone(url: str, dest: Path, subpath: str):
    if dest.exists():
        print(f"  skip (exists): {dest.name}")
        return
    subprocess.run(
        ["git", "clone", "--depth=1", "--filter=blob:none", "--sparse", url, str(dest)],
        check=True,
    )
    subprocess.run(
        ["git", "-C", str(dest), "sparse-checkout", "set", subpath], check=True
    )
    print(f"  done: {dest.name} ({subpath})")


if __name__ == "__main__":
    # print("=== Spring AI source code ===")
    # clone(
    #     "git@github.com:spring-projects/spring-ai.git",
    #     DATASETS / "projects" / "spring-ai-src",
    # )

    print("\n=== Spring AI issues ===")
    download_issues(
        "spring-projects/spring-ai", DATASETS / "projects" / "spring-ai-issues"
    )

    print("\n=== Spring AI pull requests ===")
    download_pulls("spring-projects/spring-ai", DATASETS / "projects" / "spring-ai-prs")

    # print("\n=== Design Patterns ===")
    # clone(
    #     "https://github.com/nilbuild/design-patterns-for-humans",
    #     DATASETS / "docs" / "design-patterns",
    # )

    # print("\n=== Code Smells ===")
    # sparse_clone(
    #     "https://github.com/Luzkan/smells",
    #     DATASETS / "docs" / "code-smells",
    #     "content/smells",
    # )

    # print("\n=== OWASP Top 10 2025 ===")
    # sparse_clone(
    #     "https://github.com/OWASP/Top10",
    #     DATASETS / "docs" / "owasp-top10",
    #     "2025/docs/en",
    # )

    print("\nAll done.")
