import requests
import json
import re


def fetch_pr(org, repo, pull_id):
    url = f"https://api.github.com/repos/{org}/{repo}/pulls/{pull_id}"
    response = requests.get(url)
    response.raise_for_status()
    return response.json()


def fetch_pr_diff(url, commit_sha=None):
    if commit_sha:
        url = url + f"/commits/{commit_sha}.diff"
    else:
        url = url + ".diff"

    response = requests.get(url)
    response.raise_for_status()
    return response.text


def parse_diff(diff_text):
    files = []
    current_file = None
    buffer = []
    in_hunk = False

    file_header_re = re.compile(r"^diff --git a/(.+?) b/(.+)$")

    for line in diff_text.splitlines(keepends=True):
        match = file_header_re.match(line)
        if match:
            # save previous file
            if current_file and buffer:
                files.append({"filepath": current_file, "content": "".join(buffer)})
                buffer = []
                in_hunk = False

            current_file = match.group(2)
            continue

        if line.startswith("@@"):
            in_hunk = True

        if in_hunk and current_file:
            buffer.append(line)

    # save last file
    if current_file and buffer:
        files.append({"filepath": current_file, "content": "".join(buffer)})

    return files


if __name__ == "__main__":
    org = "spring-projects"
    repo = "spring-ai"
    pr_ids = ["3801", "4166", "4256", "4597"]

    results = []
    for pr_id in pr_ids:
        pr_json = fetch_pr(org, repo, pr_id)
        pr_json = {
            "id": pr_json["id"],
            "html_url": pr_json["html_url"],
            "title": pr_json["title"],
            "body": pr_json["body"],
        }
        diff_text = fetch_pr_diff(pr_json["html_url"])
        diff = parse_diff(diff_text)
        result = pr_json | {"hunks": diff}
        results.append(result)

    open("pull_request.json", "w").write(json.dumps(results, indent=2))
