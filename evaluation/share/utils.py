import json
import re
import sys
from pathlib import Path
from typing import Any, List, Dict, Iterator, TextIO
from pathlib import Path


def get_filename_from_pathstring(path: str) -> str:
    return path.strip().split("/")[-1]


def camel_to_snake(obj):
    if isinstance(obj, dict):
        return {
            re.sub(r"(?<!^)(?=[A-Z])", "_", k).lower(): camel_to_snake(v)
            for k, v in obj.items()
        }
    elif isinstance(obj, list):
        return [camel_to_snake(x) for x in obj]
    return obj


def clamp01(value: float) -> float:
    return max(0.0, min(1.0, value))


def clamp15(value: float) -> int:
    return int(max(1.0, min(5.0, round(value))))


def safe_div(numerator: float, denominator: float) -> float:
    if denominator == 0:
        return 0.0
    return numerator / denominator


def load_json(path: Path) -> Dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def load_jsonl_entry(path: Path, index: int) -> Dict[str, Any]:
    lines = path.read_text(encoding="utf-8").splitlines()
    if index < 0 and index >= len(lines):
        return {}
    return json.loads(lines[index])


def append_jsonl_entry(file: TextIO, entry: Dict[str, Any]) -> None:
    file.write(json.dumps(entry, ensure_ascii=False) + "\n")
    file.flush()


def iter_jsonl_entries(path: Path) -> Iterator[Dict[str, Any]]:
    with path.open(encoding="utf-8") as file:
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


def render_prompt_template(template: str, variables: Dict[str, str]) -> str:
    rendered = template
    for key, value in variables.items():
        rendered = rendered.replace(f"{{{key}}}", value)
    return rendered
