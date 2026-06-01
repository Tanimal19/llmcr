import re
from typing import Set

PASCAL_CASE_PATTERN = re.compile(r"\b[A-Z][a-z0-9]+(?:[A-Z][A-Za-z0-9]*)+\b")
CAMEL_CASE_PATTERN = re.compile(r"\b[a-z]+(?:[A-Z][A-Za-z0-9]*)+\b")
IGNORED_IDENTIFIERS = {
    "build",
    "builder",
    "context",
    "prompt",
    "put",
    "mutate",
}


def _normalize_identifier(value: str) -> str:
    text = (value or "").strip()
    if not text:
        return ""

    text = re.sub(r"\s*\([^)]*\)\s*$", "", text)
    match = re.search(r"[A-Za-z_][A-Za-z0-9_]*", text)
    return match.group(0) if match else ""


def extract_java_entities(text: str) -> Set[str]:
    if not text or not text.strip():
        return set()

    entities: Set[str] = set()

    for match in PASCAL_CASE_PATTERN.findall(text):
        normalized = _normalize_identifier(match)
        if normalized and normalized.lower() not in IGNORED_IDENTIFIERS:
            entities.add(normalized)

    for match in CAMEL_CASE_PATTERN.findall(text):
        normalized = _normalize_identifier(match)
        if normalized and normalized.lower() not in IGNORED_IDENTIFIERS:
            entities.add(normalized)

    return entities
