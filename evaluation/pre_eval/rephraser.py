# This script reads raw PR discussion data from a JSONL file, calls an LLM to rewrite and normalize the comments and description into concise sentences, and writes the enriched data to a new JSONL file for evaluation.

import json
import re
import sys
import time
from pathlib import Path
from typing import Any, Dict, Iterator, List, Optional, TextIO, Tuple

import requests

INPUT_JSONL = Path(__file__).parent / "exports" / "raw.jsonl"
OUTPUT_JSONL = Path(__file__).parent / "exports" / "eval.jsonl"
API_BASE = "http://localhost:8080/v1"
MODEL = "nemotron-3-4b"
API_KEY: Optional[str] = None


def iter_entries(jsonl_path: Path) -> Iterator[Dict[str, Any]]:
    with jsonl_path.open(encoding="utf-8") as file:
        for lineno, raw in enumerate(file, 1):
            line = raw.strip()
            if not line:
                continue
            try:
                obj = json.loads(line)
            except json.JSONDecodeError as exc:
                print(f"[WARN] skip line {lineno}: {exc}", file=sys.stderr)
                continue
            if isinstance(obj, dict):
                yield obj


def normalize_space(text: str) -> str:
    return re.sub(r"\s+", " ", (text or "")).strip()


def dedupe_text_list(items: List[str]) -> List[str]:
    deduped: List[str] = []
    seen: set[str] = set()

    for item in items:
        normalized = normalize_space(item)
        if not normalized:
            continue
        key = re.sub(r"[^\w\u4e00-\u9fff]+", "", normalized.lower())
        if not key:
            key = normalized.lower()
        if key in seen:
            continue
        seen.add(key)
        deduped.append(normalized)

    return deduped


def build_comments_payload(comments: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    payload: List[Dict[str, Any]] = []
    for comment in comments:
        body = normalize_space(str(comment.get("body") or ""))
        if not body:
            continue
        payload.append(
            {
                "type": comment.get("type"),
                "poster": comment.get("poster"),
                "created_at": comment.get("created_at"),
                "state": comment.get("state"),
                "body": body,
            }
        )
    return payload


def build_prompt(entry: Dict[str, Any]) -> str:
    comments = build_comments_payload(entry.get("comments") or [])
    description = str(entry.get("pr_description") or "")

    return (
        "You are rewriting pull request discussion data for evaluation.\n"
        "Given one PR, produce strict JSON with this schema:\n"
        "{\n"
        '  "review_sentences": [string, ...],\n'
        '  "description_sentences": [string, ...]\n'
        "}\n"
        "Rules:\n"
        "1) review_sentences: merge and rewrite original comments into concise, clear statements.\n"
        "2) Remove duplicates and near-duplicates.\n"
        "3) Preserve important technical meaning; do not invent facts.\n"
        "4) description_sentences: rewrite PR description and motivation into clear atomic sentences.\n"
        "5) Remove duplicate or overlapping sentence meanings.\n"
        "6) Keep output language consistent with source text.\n"
        "7) Output valid JSON only. No markdown fences and no explanation.\n\n"
        f"PR title: {entry.get('title', '')}\n\n"
        f"PR description:\n{description}\n\n"
        f"PR comments JSON:\n{json.dumps(comments, ensure_ascii=False, indent=2)}"
    )


def parse_json_from_text(text: str) -> Dict[str, Any]:
    stripped = text.strip()
    if stripped.startswith("```"):
        stripped = re.sub(r"^```[a-zA-Z]*\n", "", stripped)
        stripped = re.sub(r"\n```$", "", stripped)

    try:
        parsed = json.loads(stripped)
        if isinstance(parsed, dict):
            return parsed
    except json.JSONDecodeError:
        pass

    start = stripped.find("{")
    end = stripped.rfind("}")
    if start != -1 and end != -1 and end > start:
        candidate = stripped[start : end + 1]
        parsed = json.loads(candidate)
        if isinstance(parsed, dict):
            return parsed

    raise ValueError("Cannot parse JSON object from LLM response")


def call_llm(
    api_base: str,
    api_key: Optional[str],
    model: str,
    prompt: str,
    timeout: int = 120,
    max_retries: int = 3,
) -> Dict[str, Any]:
    endpoint = api_base.rstrip("/") + "/chat/completions"
    headers = {"Content-Type": "application/json"}
    if api_key:
        headers["Authorization"] = f"Bearer {api_key}"

    payload = {
        "model": model,
        "temperature": 0.2,
        "messages": [
            {
                "role": "system",
                "content": "Return strict JSON only.",
            },
            {
                "role": "user",
                "content": prompt,
            },
        ],
    }

    last_error: Optional[Exception] = None
    for attempt in range(1, max_retries + 1):
        try:
            response = requests.post(
                endpoint,
                headers=headers,
                json=payload,
                timeout=timeout,
            )
            response.raise_for_status()
            data = response.json()
            content = data.get("choices", [{}])[0].get("message", {}).get("content", "")
            if not isinstance(content, str) or not content.strip():
                raise ValueError("Empty LLM content")
            return parse_json_from_text(content)
        except Exception as exc:
            last_error = exc
            if attempt < max_retries:
                time.sleep(1.2 * attempt)

    raise RuntimeError(f"LLM request failed after retries: {last_error}")


def ensure_lists(output: Dict[str, Any]) -> Tuple[List[str], List[str]]:
    review_sentences_raw = output.get("review_sentences")
    desc_raw = output.get("description_sentences")

    review_sentences = (
        review_sentences_raw if isinstance(review_sentences_raw, list) else []
    )
    desc = desc_raw if isinstance(desc_raw, list) else []

    review_sentences_text = [
        str(item) for item in review_sentences if str(item).strip()
    ]
    desc_text = [str(item) for item in desc if str(item).strip()]

    return dedupe_text_list(review_sentences_text), dedupe_text_list(desc_text)


def append_jsonl_entry(file: TextIO, entry: Dict[str, Any]) -> None:
    file.write(json.dumps(entry, ensure_ascii=False) + "\n")
    file.flush()


def main() -> None:
    if not INPUT_JSONL.exists():
        print(f"Error: input not found: {INPUT_JSONL}", file=sys.stderr)
        sys.exit(1)

    OUTPUT_JSONL.parent.mkdir(parents=True, exist_ok=True)

    total_pr_count = 0
    original_comments_count = 0
    generated_review_sentences_count = 0
    generated_description_sentences_count = 0

    print(f"Reading entries from {INPUT_JSONL}")
    print(f"Using model={MODEL}, api_base={API_BASE}")
    print(f"Writing output to {OUTPUT_JSONL}")

    with OUTPUT_JSONL.open("w", encoding="utf-8") as output_file:
        for index, entry in enumerate(iter_entries(INPUT_JSONL), 1):
            pr_id = entry.get("pr_id", f"index_{index}")
            print(f"[{index}] processing PR {pr_id}")

            prompt = build_prompt(entry)

            try:
                llm_result = call_llm(
                    api_base=API_BASE,
                    api_key=API_KEY,
                    model=MODEL,
                    prompt=prompt,
                )
                normalized_review_sentences, normalized_description = ensure_lists(
                    llm_result
                )

                merged = dict(entry)
                merged["normalized_review_sentences"] = normalized_review_sentences
                merged["normalized_description_sentences"] = normalized_description

                append_jsonl_entry(output_file, merged)

                total_pr_count += 1
                original_comments_count += len(
                    build_comments_payload(entry.get("comments") or [])
                )
                generated_review_sentences_count += len(normalized_review_sentences)
                generated_description_sentences_count += len(normalized_description)

            except Exception as exc:
                print(f"  [WARN] LLM failed for PR {pr_id}: {exc}", file=sys.stderr)

    if total_pr_count == 0:
        print("No valid entries found in input.", file=sys.stderr)
        sys.exit(1)

    print(f"Wrote {total_pr_count} entries to {OUTPUT_JSONL}")
    print("\n=== Summary ===")
    print(f"Original comments count: {original_comments_count}")
    print(f"Generated review_sentences count: {generated_review_sentences_count}")
    print(
        "Generated description sentences count: "
        f"{generated_description_sentences_count}"
    )
    print(
        f"Average generated review_sentences per PR: "
        f"{generated_review_sentences_count / total_pr_count:.2f}"
    )
    print(
        "Average generated description sentences per PR: "
        f"{generated_description_sentences_count / total_pr_count:.2f}"
    )


if __name__ == "__main__":
    main()
