import os
import time
import json
import re
from typing import Any, List, Dict, Optional
from google import genai
from google.genai import types


def call_gemini(
    model: str,
    prompt: str,
    max_retries: int = 1,
) -> Dict[str, Any]:

    api_key = os.getenv("GOOGLE_GEMINI_API_KEY")
    client = genai.Client(api_key=api_key)
    config = types.GenerateContentConfig(temperature=0.2)

    last_error: Optional[Exception] = None
    for attempt in range(1, max_retries + 1):
        try:
            response = client.models.generate_content(
                model=model,
                contents=prompt,
                config=config,
            )
            content = _extract_gemini_response_text(response)
            return _parse_dict_from_text(content)
        except Exception as exc:
            last_error = exc
            if attempt < max_retries:
                time.sleep(1.2 * attempt)

    raise RuntimeError(f"LLM request failed after retries: {last_error}")


def _extract_gemini_response_text(response: Any) -> str:
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


def _parse_dict_from_text(text: str) -> Dict[str, Any]:
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
