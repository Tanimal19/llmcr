import json
import importlib
import os
import re
from dataclasses import asdict, is_dataclass
from pathlib import Path
from typing import Any, Dict, List, Optional

DEFAULT_GEMINI_MODEL = "gemini-3.1-pro-preview"
PROMPT_FILE = Path(__file__).with_name("eval.prompt")

_PROMPT_CACHE: Optional[Dict[str, str]] = None


def _clamp15(value: float) -> float:
    return max(1.0, min(5.0, value))


def _to_float(value: Any, default: float = 0.0) -> float:
    try:
        return float(value)
    except (TypeError, ValueError):
        return default


def _to_score_1_5(value: Any, default: float = 1.0) -> int:
    numeric = _to_float(value, default=default)
    return int(round(_clamp15(numeric)))


def _to_string_list(value: Any) -> List[str]:
    if isinstance(value, list):
        return [str(item).strip() for item in value if str(item).strip()]
    if isinstance(value, str):
        text = value.strip()
        return [text] if text else []
    return []


def _to_pretty_json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, indent=2)


def _to_jsonable(value: Any) -> Any:
    if is_dataclass(value) and not isinstance(value, type):
        return asdict(value)
    if isinstance(value, dict):
        return {str(key): _to_jsonable(item) for key, item in value.items()}
    if isinstance(value, list):
        return [_to_jsonable(item) for item in value]
    return value


def _as_dict(value: Any) -> Dict[str, Any]:
    jsonable = _to_jsonable(value)
    return jsonable if isinstance(jsonable, dict) else {}


def _extract_prompt_sections(content: str) -> Dict[str, str]:
    pattern = re.compile(
        r"\[(?P<name>[A-Z0-9_]+)\]\s*(?P<body>[\s\S]*?)\s*\[/\1\]",
        re.MULTILINE,
    )
    sections: Dict[str, str] = {}
    for match in pattern.finditer(content):
        name = match.group("name").strip()
        body = match.group("body").strip()
        if name and body:
            sections[name] = body
    return sections


def _load_prompt_sections() -> Dict[str, str]:
    global _PROMPT_CACHE
    if _PROMPT_CACHE is not None:
        return _PROMPT_CACHE

    if not PROMPT_FILE.exists():
        _PROMPT_CACHE = {}
        return _PROMPT_CACHE

    content = PROMPT_FILE.read_text(encoding="utf-8")
    _PROMPT_CACHE = _extract_prompt_sections(content)
    return _PROMPT_CACHE


def _render_prompt_template(template: str, variables: Dict[str, str]) -> str:
    rendered = template
    for key, value in variables.items():
        rendered = rendered.replace(f"{{{key}}}", value)
    return rendered


def _build_quality_prompt(
    parsed_review: Dict[str, Any],
    pr_entry: Dict[str, Any],
    static_analysis_results: str,
) -> str:
    sections = _load_prompt_sections()
    template = sections.get("QUALITY_SCORE_PROMPT")

    if template is None:
        raise RuntimeError("QUALITY_SCORE_PROMPT section is missing in the prompt file")

    parsed_review_dict = _as_dict(parsed_review)
    compact_pr = _compact_pr_entry(_as_dict(pr_entry))

    return _render_prompt_template(
        template,
        {
            "pull_request": _to_pretty_json(compact_pr),
            "static_analysis_messages": _to_pretty_json(static_analysis_results),
            "review_report": _to_pretty_json(parsed_review_dict),
        },
    )


def _extract_json_block(text: str) -> Dict[str, Any]:
    stripped = (text or "").strip()
    if not stripped:
        raise ValueError("empty model response")

    try:
        return json.loads(stripped)
    except json.JSONDecodeError:
        pass

    fenced = re.search(r"```(?:json)?\s*(\{[\s\S]*\})\s*```", stripped)
    if fenced:
        return json.loads(fenced.group(1))

    start = stripped.find("{")
    end = stripped.rfind("}")
    if start >= 0 and end > start:
        return json.loads(stripped[start : end + 1])

    raise ValueError("model response does not contain valid JSON")


def _get_api_key() -> str:
    api_key = os.getenv("GOOGLE_GEMINI_API_KEY")
    if not api_key:
        raise RuntimeError(
            "Missing GEMINI_API_KEY (or GOOGLE_API_KEY) for LLM-as-a-judge"
        )
    return api_key


def _extract_response_text(response: Any) -> str:
    direct_text = getattr(response, "text", None)
    if isinstance(direct_text, str) and direct_text.strip():
        return direct_text

    candidates = getattr(response, "candidates", None) or []
    parts: List[str] = []
    for candidate in candidates:
        content = getattr(candidate, "content", None)
        if not content:
            continue
        content_parts = getattr(content, "parts", None) or []
        for part in content_parts:
            part_text = getattr(part, "text", None)
            if isinstance(part_text, str) and part_text.strip():
                parts.append(part_text)

    if parts:
        return "\n".join(parts)

    raise RuntimeError("Gemini returned no text output")


def _compact_pr_entry(pr_entry: Optional[Dict[str, Any]]) -> Dict[str, Any]:
    if not isinstance(pr_entry, dict):
        return {}

    compact: Dict[str, Any] = {
        "pr_id": pr_entry.get("pr_id") or pr_entry.get("prId"),
        "url": pr_entry.get("url"),
        "title": pr_entry.get("title") or pr_entry.get("pr_title") or "",
        "pr_description": pr_entry.get("pr_description")
        or pr_entry.get("description")
        or "",
        "is_closed": bool(pr_entry.get("is_closed", False)),
        "is_merged": bool(pr_entry.get("is_merged", False)),
        "is_approved": bool(pr_entry.get("is_approved", False)),
    }

    comments_raw = pr_entry.get("comments")
    comments: List[Dict[str, Any]] = []
    if isinstance(comments_raw, list):
        for comment in comments_raw:
            if not isinstance(comment, dict):
                continue
            comments.append(
                {
                    "type": str(comment.get("type") or ""),
                    "poster": comment.get("poster"),
                    "created_at": comment.get("created_at"),
                    "body": str(comment.get("body") or ""),
                    "state": comment.get("state"),
                }
            )
    compact["comments"] = comments

    changed_files_raw = pr_entry.get("changed_files") or []
    changed_files: List[Dict[str, Any]] = []
    if isinstance(changed_files_raw, list):
        for file_info in changed_files_raw:
            if not isinstance(file_info, dict):
                continue
            path = str(file_info.get("path") or "").strip()
            previous_path_raw = file_info.get("previous_path")
            previous_path = (
                str(previous_path_raw).strip()
                if previous_path_raw is not None
                else None
            )
            patch = str(file_info.get("patch") or "").strip()
            content = str(file_info.get("content") or "").strip()
            changed_files.append(
                {
                    "path": path,
                    "previous_path": previous_path,
                    "patch": patch,
                    "content": content,
                }
            )

    compact["changed_files"] = changed_files
    compact["normalized_review_sentences"] = pr_entry.get("normalized_review_sentences")
    compact["normalized_description_sentences"] = pr_entry.get(
        "normalized_description_sentences"
    )
    return compact


def _call_gemini_json(prompt: str, model_name: str) -> Dict[str, Any]:
    print(f"Raw prompt:\n{prompt}\n")
    genai = importlib.import_module("google.genai")
    client = genai.Client(api_key=_get_api_key())
    response = client.models.generate_content(
        model=model_name,
        contents=prompt,
        config={
            "temperature": 0,
            "response_mime_type": "application/json",
        },
    )
    print(f"Gemini raw response: {response}")
    text = _extract_response_text(response)
    return _extract_json_block(text)


def judge_quality_score(
    parsed_review: Dict[str, Any],
    pr_entry: Dict[str, Any],
    static_analysis_results: str,
    model_name: str = DEFAULT_GEMINI_MODEL,
) -> Dict[str, Any]:
    prompt = _build_quality_prompt(
        parsed_review=parsed_review,
        pr_entry=pr_entry,
        static_analysis_results=static_analysis_results,
    )

    result = _call_gemini_json(prompt, model_name=model_name)
    return {
        "comprehensiveness": _to_score_1_5(result.get("comprehensiveness"), 1.0),
        "conciseness": _to_score_1_5(result.get("conciseness"), 1.0),
        "relevance": _to_score_1_5(result.get("relevance"), 1.0),
        "judge_full_output": result,
    }
