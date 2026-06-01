# This script reads raw PR discussion data from a JSONL file, calls an LLM to rewrite and normalize the comments and description into concise sentences, and writes the enriched data to a new JSONL file for evaluation.

import json
import os
import re
import sys
import time
from pathlib import Path
from typing import Any, Dict, Iterator, List, Optional, TextIO, Tuple

from google import genai
from google.genai import types

INPUT_JSONL = Path(__file__).parent.parent / "exports" / "raw.jsonl"
OUTPUT_JSONL = Path(__file__).parent.parent / "exports" / "eval.jsonl"
MODEL = "gemini-2.5-flash"
API_KEY: Optional[str] = os.getenv("GOOGLE_GEMINI_API_KEY")


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
        "1) review_sentences: merge and rewrite original comments into clear statements.\n"
        "2) description_sentences: rewrite PR description and motivation into clear atomic sentences.\n"
        "3) Remove duplicates and near-duplicates.\n"
        "4) Preserve important technical meaning; do not invent facts.\n"
        "5) Remove duplicate or overlapping sentence meanings.\n"
        "6) Keep output language consistent with source text.\n"
        "7) Do not include any sentences that are purely about process, politeness, or non-technical content.\n"
        "8) Output no more than 8 sentences in review_sentences and 8 sentences in description_sentences.\n"
        "9) Output valid JSON only. No markdown fences and no explanation.\n\n"
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


def extract_response_text(response: Any) -> str:
    text = getattr(response, "text", None)
    if isinstance(text, str) and text.strip():
        return text

    candidates = getattr(response, "candidates", None)
    if isinstance(candidates, list):
        chunks: List[str] = []
        for candidate in candidates:
            content = getattr(candidate, "content", None)
            parts = getattr(content, "parts", None) if content is not None else None
            if not isinstance(parts, list):
                continue
            for part in parts:
                part_text = getattr(part, "text", None)
                if isinstance(part_text, str) and part_text.strip():
                    chunks.append(part_text)
        if chunks:
            return "\n".join(chunks)

    raise ValueError("Empty LLM content")


def call_llm(
    api_key: Optional[str],
    model: str,
    prompt: str,
    timeout: int = 120,
    max_retries: int = 3,
) -> Dict[str, Any]:
    if not api_key:
        raise RuntimeError("Missing API key: set GEMINI_API_KEY or GOOGLE_API_KEY")

    client = genai.Client(api_key=api_key)
    config = types.GenerateContentConfig(
        temperature=0.2,
        response_mime_type="application/json",
        system_instruction="Return strict JSON only.",
    )

    last_error: Optional[Exception] = None
    for attempt in range(1, max_retries + 1):
        try:
            response = client.models.generate_content(
                model=model,
                contents=prompt,
                config=config,
            )
            content = extract_response_text(response)
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

    if not API_KEY:
        print(
            "Error: missing API key. Set GEMINI_API_KEY or GOOGLE_API_KEY.",
            file=sys.stderr,
        )
        sys.exit(1)

    OUTPUT_JSONL.parent.mkdir(parents=True, exist_ok=True)

    total_pr_count = 0
    original_comments_count = 0
    generated_review_sentences_count = 0
    generated_description_sentences_count = 0

    print(f"Reading entries from {INPUT_JSONL}")
    print(f"Using model={MODEL} via google-genai SDK")
    print(f"Writing output to {OUTPUT_JSONL}")

    with OUTPUT_JSONL.open("w", encoding="utf-8") as output_file:
        for index, entry in enumerate(iter_entries(INPUT_JSONL), 1):
            pr_id = entry.get("pr_id", f"index_{index}")
            print(f"[{index}] processing PR {pr_id}")

            prompt = build_prompt(entry)

            try:
                llm_result = call_llm(
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
