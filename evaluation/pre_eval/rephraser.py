# This script reads raw PR discussion data from a JSONL file, calls an LLM to rewrite and normalize the comments and description into concise sentences, and writes the enriched data to a new JSONL file for evaluation.

import re
import sys
from dataclasses import asdict
from pathlib import Path
from typing import Any, Dict, List, Tuple
from dacite import from_dict
from share.utils import (
    iter_jsonl_entries,
    append_jsonl_entry,
    render_prompt_template,
)
from share.llm import call_gemini
from share.pull_request_scheme import PullRequestEntry
from config import RAW_PULL_REQUEST_JSONL, REPHRASED_PULL_REQUEST_JSONL

INPUT_JSONL = RAW_PULL_REQUEST_JSONL
OUTPUT_JSONL = REPHRASED_PULL_REQUEST_JSONL
PROMPT_FILE = Path(__file__).parent / "rephrase.prompt.txt"
MODEL = "gemini-2.5-flash"


def _dedupe_text_list(items: List[str]) -> List[str]:
    deduped: List[str] = []
    seen: set[str] = set()

    for item in items:
        normalized = re.sub(r"\s+", " ", item).strip()
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


def _parse_response(output: Dict[str, Any]) -> Tuple[List[str], List[str]]:
    desc_raw = output.get("description_sentences")
    comm_raw = output.get("comment_sentences")

    desc = desc_raw if isinstance(desc_raw, list) else []
    comm = comm_raw if isinstance(comm_raw, list) else []

    desc_text = [str(item) for item in desc if str(item).strip()]
    comm_text = [str(item) for item in comm if str(item).strip()]

    return _dedupe_text_list(desc_text), _dedupe_text_list(comm_text)


def main() -> None:
    prompt_template = PROMPT_FILE.read_text(encoding="utf-8")

    with OUTPUT_JSONL.open("w", encoding="utf-8") as output_file:
        for index, data in enumerate(iter_jsonl_entries(INPUT_JSONL), 1):
            pr = from_dict(data_class=PullRequestEntry, data=data)
            print(f"[{index}] processing PR {pr.id}")

            prompt = render_prompt_template(
                prompt_template,
                {
                    "pr_title": pr.title,
                    "pr_description": pr.description,
                    "comments": str(pr.comments),
                },
            )

            try:
                response = call_gemini(model=MODEL, prompt=prompt)
                pr.rephrased_description, pr.rephrased_comments = _parse_response(
                    response
                )
                append_jsonl_entry(output_file, asdict(pr))

            except Exception as exc:
                print(f"[WARN] LLM failed for PR {pr.id}: {exc}", file=sys.stderr)


if __name__ == "__main__":
    main()
