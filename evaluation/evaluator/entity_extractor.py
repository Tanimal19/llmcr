import re
from functools import lru_cache
from typing import Set
from gliner import GLiNER

# Matches identifiers like `ClassName` or `HTTPServer`.
PASCAL_CASE_PATTERN = re.compile(r"\b(?:[A-Z][a-z0-9]*){2,}\b")

# Matches identifiers like `methodName` or `httpServer`.
CAMEL_CASE_PATTERN = re.compile(r"\b[a-z]+(?:[A-Z][a-z0-9]*)+\b")

# Matches identifiers like `MAX_BUFFER_SIZE`.
UPPER_SNAKE_CASE_PATTERN = re.compile(r"\b[A-Z]+(?:_[A-Z0-9]+)+\b")

# Matches annotations/decorators like `@Override` or `@dataclass`.
ANNOTATION_PATTERN = re.compile(r"@[A-Za-z_][A-Za-z0-9_]*")


ENTITY_PATTERNS = {
	"pascal_case": PASCAL_CASE_PATTERN,
	"camel_case": CAMEL_CASE_PATTERN,
	"upper_snake_case": UPPER_SNAKE_CASE_PATTERN,
	"annotation": ANNOTATION_PATTERN,
}

GLINER_MODEL = "urchade/gliner_medium-v2"
MODEL_MAX_LENGTH = 1024
GLINER_ENTITY_LABELS = ["java class", "java method" ,"java field", "file name"]


@lru_cache(maxsize=1)
def _get_gliner_model():
    return GLiNER.from_pretrained(GLINER_MODEL)

def normalize_entity(entity: str) -> str:
    # Remove common suffixes like "()", "[]", "<>", etc.
    entity = re.sub(r"[\(\)\[\]<>]+$", "", entity)
    return entity.strip()

def extract_entities_regex(text: str) -> Set[str]:
    entities = {key: pattern.findall(text) for key, pattern in ENTITY_PATTERNS.items()}
    return set(normalize_entity(entity) for sublist in entities.values() for entity in sublist)


def extract_entities_ner(text: str) -> Set[str]:
    model = _get_gliner_model()

    # split text into chunks seperated by space, ensuring that we don't split in the middle of an entity
    chunks = []
    current_chunk = ""
    for token in text.split():
        if len(current_chunk) + len(token) + 1 > MODEL_MAX_LENGTH:
            chunks.append(current_chunk)
            current_chunk = token
        else:
            current_chunk += " " + token if current_chunk else token
    if current_chunk:
        chunks.append(current_chunk)

    entities = []
    for chunk in chunks:
        print(f"GLiNER: extracting entities from chunk: {chunk[:50]}...")
        entities.extend(model.predict_entities(chunk, GLINER_ENTITY_LABELS))

    return set(normalize_entity(entity["text"]) for entity in entities)
    
def extract_entities(text: str) -> Set[str]:
    regex_entities = extract_entities_regex(text)
    ner_entities = extract_entities_ner(text)
    return regex_entities | ner_entities