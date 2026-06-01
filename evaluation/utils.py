import re
import json
from pathlib import Path
from typing import Any, List, Dict, Optional


def safe_div(numerator: float, denominator: float) -> float:
    if denominator == 0:
        return 0.0
    return numerator / denominator


def clamp01(value: float) -> float:
    return max(0.0, min(1.0, value))


def clamp15(value: float) -> int:
    return int(max(1.0, min(5.0, round(value))))


def word_count(text: str) -> int:
    return len(re.findall(r"\b\w+\b", text or ""))


def split_sentences(text: str) -> List[str]:
    chunks = re.split(r"(?<=[.!?。！？])\s+|\n+", text or "")
    return [chunk.strip() for chunk in chunks if chunk.strip()]


def to_text(value: Any) -> str:
    if value is None:
        return ""
    return str(value)


def to_string_list(value: Any) -> List[str]:
    if not isinstance(value, list):
        return []
    return [str(item).strip() for item in value if str(item).strip()]


def load_json(path: Path) -> Dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def load_jsonl_first(path: Path) -> Dict[str, Any]:
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if stripped:
            return json.loads(stripped)
    return {}


def to_int(value: Any) -> Optional[int]:
    if isinstance(value, bool):
        return None
    if isinstance(value, int):
        return value
    if isinstance(value, str):
        text = value.strip()
        if text and re.fullmatch(r"-?\d+", text):
            return int(text)
    return None
