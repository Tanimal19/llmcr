import json
import re
import sys
from dataclasses import dataclass, field
from difflib import SequenceMatcher
from pathlib import Path
from typing import Any, Dict, List, Optional, Set, Tuple, cast

try:
    from bert_score import score as bert_score_fn
except Exception:  # pragma: no cover - optional dependency
    bert_score_fn = None


ALIGNMENT_METHOD = "auto"
BERT_LANG = "en"
BERT_MODEL_TYPE: Optional[str] = None
REPETITIVE_THRESHOLD = 0.9
ISSUE_DETAIL_MIN_WORDS = 8

# TODO(LLM-as-a-judge): replace these mock values by real LLM judge outputs.
MOCK_ISSUE_CORRECTNESS = 0.6
MOCK_QUALITY_COMPREHENSIVENESS = 0.7
MOCK_QUALITY_CONCISENESS = 0.6
MOCK_QUALITY_RELEVANCE = 0.65


def safe_div(numerator: float, denominator: float) -> float:
    if denominator == 0:
        return 0.0
    return numerator / denominator


def clamp01(value: float) -> float:
    return max(0.0, min(1.0, value))


def word_count(text: str) -> int:
    return len(re.findall(r"\b\w+\b", text or ""))


def split_sentences(text: str) -> List[str]:
    chunks = re.split(r"(?<=[.!?。！？])\s+|\n+", text or "")
    return [chunk.strip() for chunk in chunks if chunk.strip()]


def sentence_similarity(a: str, b: str) -> float:
    return SequenceMatcher(None, (a or "").lower(), (b or "").lower()).ratio()


def greedy_sentence_alignment(
    references: List[str], candidates: List[str]
) -> Tuple[float, float, float]:
    if not references and not candidates:
        return 0.0, 0.0, 0.0

    if references and candidates:
        precision_sum = 0.0
        for candidate in candidates:
            precision_sum += max(
                sentence_similarity(candidate, reference) for reference in references
            )
        precision = precision_sum / len(candidates)

        recall_sum = 0.0
        for reference in references:
            recall_sum += max(
                sentence_similarity(reference, candidate) for candidate in candidates
            )
        recall = recall_sum / len(references)
    else:
        precision = 0.0
        recall = 0.0

    f1 = safe_div(2 * precision * recall, precision + recall)
    return precision, recall, f1


def bert_sentence_alignment(
    references: List[str],
    candidates: List[str],
    lang: str = "en",
    model_type: Optional[str] = None,
    verbose: bool = False,
) -> Tuple[float, float, float]:
    if not references and not candidates:
        return 0.0, 0.0, 0.0
    if not references or not candidates:
        return 0.0, 0.0, 0.0
    if bert_score_fn is None:
        return greedy_sentence_alignment(references, candidates)

    refs = [" ".join(references)]
    cands = [" ".join(candidates)]

    kwargs: Dict[str, Any] = {"lang": lang, "verbose": verbose}
    if model_type:
        kwargs["model_type"] = model_type

    raw_result = cast(Any, bert_score_fn(cands=cands, refs=refs, **kwargs))
    precision, recall, f1 = raw_result
    p = (
        float(precision.mean().item())
        if hasattr(precision, "mean")
        else float(precision)
    )
    r = float(recall.mean().item()) if hasattr(recall, "mean") else float(recall)
    f = float(f1.mean().item()) if hasattr(f1, "mean") else float(f1)
    return p, r, f


def compute_repetitive_rate(sentences: List[str], threshold: float = 0.9) -> float:
    if not sentences:
        return 0.0

    clusters: List[List[str]] = []
    for sentence in sentences:
        placed = False
        for cluster in clusters:
            if sentence_similarity(sentence, cluster[0]) >= threshold:
                cluster.append(sentence)
                placed = True
                break
        if not placed:
            clusters.append([sentence])

    return 1.0 - safe_div(len(clusters), len(sentences))


@dataclass
class ChecklistEvidence:
    filepath: str
    lines: str
    reason: str


@dataclass
class ChecklistItem:
    title: str
    final_answer: str
    analysis: str
    evidences: List[ChecklistEvidence] = field(default_factory=list)
    final_answer_labeled: bool = False
    analysis_labeled: bool = False
    expected_evidence_count: int = 0


@dataclass
class Issue:
    issue_type: str
    title: str
    location: str
    detail: str


@dataclass
class ParsedReview:
    motivation: str = ""
    good_points: str = ""
    bad_points: str = ""
    suggestion: str = ""
    implementation_details: str = ""
    issues: List[Issue] = field(default_factory=list)
    change_description: str = ""
    change_motivation: str = ""
    checklist_items: List[ChecklistItem] = field(default_factory=list)


SECTION_PATTERNS = {
    "motivation": r"##\s+Motivation",
    "good_points": r"##\s+Good\s+Points",
    "bad_points": r"##\s+Bad\s+Points",
    "suggestion": r"##\s+Suggestion",
    "implementation_details": r"##\s+Implementation\s+Details",
    "issues": r"##\s+Issues",
    "change_description": r"###\s+Change\s+Description",
    "change_motivation": r"###\s+Change\s+Motivation",
}

CHECKLIST_HEADER_PATTERN = r"###\s+Checklist\s+Item(?:\s*:)?\s*(.*)"
CHECKLIST_START_PATTERN = r"#\s+Appendix:\s+Detailed\s+Checklist\s+Item\s+Answers"
FINAL_ANSWER_PATTERN = r"Final\s+Answer\s*:\s*(.+)"
ANALYSIS_PATTERN = r"Analysis\s*:\s*(.+)"
EVIDENCE_TRIPLE_COLON_PATTERN = (
    r"[-*]\s+([^:\n]+?\.java):::([0-9]+\s*[-~]\s*[0-9]+|[0-9]+-[0-9]+):::(.+)"
)
EVIDENCE_BULLET_PATTERN = (
    r"[-*]\s+([^\n]+?\.java)\s*\(\s*lines?\s*:\s*([^)]+)\)\s*\n\s*[-*]\s+([^\n]+)"
)


def extract_labeled_text(body: str, label_pattern: str) -> Tuple[str, bool]:
    match = re.search(label_pattern, body, re.IGNORECASE | re.DOTALL)
    if not match:
        return "", False
    return match.group(1).strip(), True


def extract_section(text: str, header_regex: str) -> str:
    header = re.search(header_regex, text, re.IGNORECASE)
    if not header:
        return ""

    rest = text[header.end() :]
    end = re.search(r"\n#{2,3}\s+", rest)
    if end:
        return rest[: end.start()].strip()
    return rest.strip()


def parse_issue_table(section_text: str) -> List[Issue]:
    issues: List[Issue] = []
    lines = [line.strip() for line in section_text.splitlines() if line.strip()]
    for line in lines:
        if not line.startswith("|"):
            continue
        if re.fullmatch(r"\|[-\s|:]+\|?", line):
            continue

        cols = [col.strip() for col in line.strip("|").split("|")]
        if len(cols) < 4:
            continue
        if cols[0].lower() == "type" and cols[1].lower() == "title":
            continue

        issues.append(
            Issue(
                issue_type=cols[0],
                title=cols[1],
                location=cols[2],
                detail=cols[3],
            )
        )
    return issues


def parse_evidences(body: str) -> List[ChecklistEvidence]:
    evidences: List[ChecklistEvidence] = []
    seen: Set[Tuple[str, str, str]] = set()

    patterns = [
        (EVIDENCE_TRIPLE_COLON_PATTERN, 1, 2, 3),
        (EVIDENCE_BULLET_PATTERN, 1, 2, 3),
    ]
    for pattern, file_idx, line_idx, reason_idx in patterns:
        for match in re.finditer(pattern, body, re.IGNORECASE):
            filepath = match.group(file_idx).strip()
            lines = match.group(line_idx).replace("~", "-").replace(" ", "")
            reason = match.group(reason_idx).strip()
            key = (filepath, lines, reason)
            if key in seen:
                continue
            seen.add(key)
            evidences.append(
                ChecklistEvidence(filepath=filepath, lines=lines, reason=reason)
            )

    return evidences


def count_expected_evidence_rows(body: str) -> int:
    triple_count = len(
        re.findall(EVIDENCE_TRIPLE_COLON_PATTERN, body, flags=re.IGNORECASE)
    )
    bullet_count = len(re.findall(EVIDENCE_BULLET_PATTERN, body, flags=re.IGNORECASE))
    return triple_count + bullet_count


def parse_checklist_item_body(
    body: str,
) -> Tuple[str, str, List[ChecklistEvidence], bool, bool, int]:
    final_answer, final_labeled = extract_labeled_text(body, FINAL_ANSWER_PATTERN)
    analysis_text, analysis_labeled = extract_labeled_text(body, ANALYSIS_PATTERN)

    if analysis_text:
        stop = re.search(r"\n\s*[-*]\s+", analysis_text)
        analysis = (
            analysis_text[: stop.start()].strip() if stop else analysis_text.strip()
        )
    else:
        analysis = ""

    paragraphs = [p.strip() for p in re.split(r"\n\s*\n", body) if p.strip()]
    if not final_answer and paragraphs:
        final_answer = paragraphs[0].splitlines()[0].strip()
    if not analysis:
        if len(paragraphs) > 1:
            analysis = paragraphs[1]
        elif paragraphs:
            analysis = paragraphs[0]

    evidences = parse_evidences(body)
    expected_evidence_count = count_expected_evidence_rows(body)

    return (
        final_answer,
        analysis,
        evidences,
        final_labeled,
        analysis_labeled,
        expected_evidence_count,
    )


def parse_checklist_items(text: str) -> List[ChecklistItem]:
    start = re.search(CHECKLIST_START_PATTERN, text, re.IGNORECASE)
    if not start:
        return []

    scope = text[start.end() :]
    headers = list(re.finditer(CHECKLIST_HEADER_PATTERN, scope, re.IGNORECASE))
    if not headers:
        return []

    items: List[ChecklistItem] = []
    for i, header in enumerate(headers):
        body_start = header.end()
        body_end = headers[i + 1].start() if i + 1 < len(headers) else len(scope)
        title = header.group(1).strip() or f"{i + 1}"
        body = scope[body_start:body_end].strip()
        (
            final_answer,
            analysis,
            evidences,
            final_labeled,
            analysis_labeled,
            expected_evidence_count,
        ) = parse_checklist_item_body(body)
        items.append(
            ChecklistItem(
                title=title,
                final_answer=final_answer,
                analysis=analysis,
                evidences=evidences,
                final_answer_labeled=final_labeled,
                analysis_labeled=analysis_labeled,
                expected_evidence_count=expected_evidence_count,
            )
        )

    return items


def parse_review_markdown(text: str) -> ParsedReview:
    return ParsedReview(
        motivation=extract_section(text, SECTION_PATTERNS["motivation"]),
        good_points=extract_section(text, SECTION_PATTERNS["good_points"]),
        bad_points=extract_section(text, SECTION_PATTERNS["bad_points"]),
        suggestion=extract_section(text, SECTION_PATTERNS["suggestion"]),
        implementation_details=extract_section(
            text, SECTION_PATTERNS["implementation_details"]
        ),
        issues=parse_issue_table(extract_section(text, SECTION_PATTERNS["issues"])),
        change_description=extract_section(
            text, SECTION_PATTERNS["change_description"]
        ),
        change_motivation=extract_section(text, SECTION_PATTERNS["change_motivation"]),
        checklist_items=parse_checklist_items(text),
    )


def normalize_line_span(text: str) -> str:
    cleaned = re.sub(r"\s+", "", (text or "").replace("~", "-"))
    match = re.fullmatch(r"(\d+)-(\d+)", cleaned)
    if not match:
        return ""
    start = int(match.group(1))
    end = int(match.group(2))
    if end < start:
        start, end = end, start
    return f"{start}-{end}"


def extract_entities_from_review(parsed: ParsedReview) -> Set[str]:
    text_fields = [
        parsed.motivation,
        parsed.good_points,
        parsed.bad_points,
        parsed.suggestion,
        parsed.implementation_details,
        parsed.change_description,
        parsed.change_motivation,
    ]
    for issue in parsed.issues:
        text_fields.extend([issue.title, issue.location, issue.detail])
    for item in parsed.checklist_items:
        text_fields.extend([item.title, item.final_answer, item.analysis])
        for evidence in item.evidences:
            text_fields.extend([evidence.filepath, evidence.lines, evidence.reason])

    text = "\n".join(text_fields)
    entities: Set[str] = set()

    entities.update(re.findall(r"[\w./-]+\.java", text))
    entities.update(re.findall(r"\b[a-z]+(?:\.[a-zA-Z_][\w]*){1,}\b", text))
    entities.update(
        re.findall(r"\b[A-Z][A-Za-z0-9_]*(?:\.[A-Z][A-Za-z0-9_]*)*\b", text)
    )
    entities.update(re.findall(r"\b[a-zA-Z_][\w]*\s*\([^)]*\)", text))

    return {entity.strip() for entity in entities if entity.strip()}


def extract_entities_from_diff(diff_text: str) -> Set[str]:
    entities: Set[str] = set()
    entities.update(re.findall(r"\+\+\+\s+b/([^\n]+)", diff_text or ""))
    entities.update(re.findall(r"---\s+a/([^\n]+)", diff_text or ""))
    entities.update(
        re.findall(r"\bpackage\s+([a-z]+(?:\.[a-zA-Z_][\w]*)+)\s*;", diff_text or "")
    )
    entities.update(
        re.findall(
            r"\b(?:class|interface|enum|record)\s+([A-Z][A-Za-z0-9_]*)\b",
            diff_text or "",
        )
    )
    entities.update(
        re.findall(
            r"\b(?:public|private|protected)?\s*(?:static\s+)?(?:final\s+)?[\w<>\[\],?\s]+\s+([a-zA-Z_][\w]*)\s*\([^;{}]*\)\s*\{",
            diff_text or "",
        )
    )
    return {entity.strip() for entity in entities if entity.strip()}


def extract_entities_from_pr(pr_entry: Dict[str, Any]) -> Set[str]:
    entities: Set[str] = set()

    changed_files = pr_entry.get("changed_files") or []
    patches: List[str] = []
    for file_info in changed_files:
        if not isinstance(file_info, dict):
            continue
        path = file_info.get("path")
        if path:
            entities.add(path)
        patch = file_info.get("patch")
        if patch:
            patches.append(patch)
        content = file_info.get("content") or ""
        if content:
            entities.update(
                re.findall(
                    r"\b(?:class|interface|enum|record)\s+([A-Z][A-Za-z0-9_]*)\b",
                    content,
                )
            )

    if patches:
        entities.update(extract_entities_from_diff("\n".join(patches)))
    elif pr_entry.get("diff") or pr_entry.get("Diff"):
        entities.update(
            extract_entities_from_diff(
                pr_entry.get("diff") or pr_entry.get("Diff") or ""
            )
        )

    return {entity.strip() for entity in entities if entity.strip()}


def truth_grounding(
    parsed: ParsedReview, pr_entry: Optional[Dict[str, Any]]
) -> Dict[str, float]:
    mentioned = extract_entities_from_review(parsed)
    real = extract_entities_from_pr(pr_entry or {}) if pr_entry else set()

    overlap = mentioned.intersection(real)
    grounding_score = safe_div(len(overlap), len(mentioned))
    coverage_score = safe_div(len(overlap), len(real))

    return {
        "grounding_score": clamp01(grounding_score),
        "coverage_score": clamp01(coverage_score),
        "mentioned_entities": float(len(mentioned)),
        "real_entities": float(len(real)),
        "matched_entities": float(len(overlap)),
    }


def collect_comment_candidates(parsed: ParsedReview) -> List[str]:
    candidate_text = "\n".join(
        [
            parsed.good_points,
            parsed.bad_points,
            parsed.suggestion,
            "\n".join(f"{issue.title}. {issue.detail}" for issue in parsed.issues),
        ]
    )
    return split_sentences(candidate_text)


def collect_comment_references(pr_entry: Optional[Dict[str, Any]]) -> List[str]:
    if not pr_entry:
        return []
    comments = pr_entry.get("comments") or pr_entry.get("Comments") or []
    references: List[str] = []
    for comment in comments:
        body = comment.get("body") if isinstance(comment, dict) else str(comment)
        references.extend(split_sentences(body or ""))
    return references


def collect_interpretation_candidates(parsed: ParsedReview) -> List[str]:
    return split_sentences(
        "\n".join([parsed.change_description, parsed.change_motivation])
    )


def collect_interpretation_references(pr_entry: Optional[Dict[str, Any]]) -> List[str]:
    if not pr_entry:
        return []
    return split_sentences(
        pr_entry.get("pr_description")
        or pr_entry.get("description")
        or pr_entry.get("Description")
        or ""
    )


def compute_alignment(
    references: List[str],
    candidates: List[str],
    method: str,
    bert_lang: str,
    bert_model_type: Optional[str],
) -> Tuple[float, float, float]:
    use_bert = method == "bert-score" or (
        method == "auto" and bert_score_fn is not None
    )
    if use_bert:
        return bert_sentence_alignment(
            references,
            candidates,
            lang=bert_lang,
            model_type=bert_model_type,
        )
    return greedy_sentence_alignment(references, candidates)


def review_alignment(
    parsed: ParsedReview,
    pr_entry: Optional[Dict[str, Any]],
    method: str = "auto",
    bert_lang: str = "en",
    bert_model_type: Optional[str] = None,
) -> Dict[str, float]:
    comment_refs = collect_comment_references(pr_entry)
    comment_cands = collect_comment_candidates(parsed)

    interp_refs = collect_interpretation_references(pr_entry)
    interp_cands = collect_interpretation_candidates(parsed)

    cp, cr, cf1 = compute_alignment(
        comment_refs,
        comment_cands,
        method,
        bert_lang,
        bert_model_type,
    )
    ip, ir, if1 = compute_alignment(
        interp_refs,
        interp_cands,
        method,
        bert_lang,
        bert_model_type,
    )

    return {
        "comment_precision": clamp01(cp),
        "comment_recall": clamp01(cr),
        "comment_f1": clamp01(cf1),
        "interpretation_precision": clamp01(ip),
        "interpretation_recall": clamp01(ir),
        "interpretation_f1": clamp01(if1),
    }


def extract_changed_file_paths(pr_entry: Optional[Dict[str, Any]]) -> Set[str]:
    if not pr_entry:
        return set()
    changed_files = pr_entry.get("changed_files") or []
    paths = set()
    for file_info in changed_files:
        if isinstance(file_info, dict):
            path = file_info.get("path")
            if path:
                paths.add(path)
    if not paths:
        diff_text = pr_entry.get("diff") or pr_entry.get("Diff") or ""
        paths.update(re.findall(r"\+\+\+\s+b/([^\n]+)", diff_text))
    return paths


def issue_correctness(
    parsed: ParsedReview, pr_entry: Optional[Dict[str, Any]]
) -> Dict[str, float]:
    issues = parsed.issues
    if not issues:
        return {"issue_correctness": 0.0, "valid_issues": 0.0, "total_issues": 0.0}

    # TODO(LLM-as-a-judge): validate each issue with an LLM judge and count True/False.
    valid_count = int(round(MOCK_ISSUE_CORRECTNESS * len(issues)))

    return {
        "issue_correctness": clamp01(MOCK_ISSUE_CORRECTNESS),
        "valid_issues": float(valid_count),
        "total_issues": float(len(issues)),
    }


def quality_score(
    grounding: Dict[str, float],
    alignment: Dict[str, float],
    issue: Dict[str, float],
    repetitive_rate: float,
) -> Dict[str, float]:
    # TODO(LLM-as-a-judge): replace with CRScore-style prompt and judge outputs.
    _ = grounding, alignment, issue, repetitive_rate

    return {
        "comprehensiveness": clamp01(MOCK_QUALITY_COMPREHENSIVENESS),
        "conciseness": clamp01(MOCK_QUALITY_CONCISENESS),
        "relevance": clamp01(MOCK_QUALITY_RELEVANCE),
    }


def evaluate_review(
    review_text: str,
    pr_entry: Optional[Dict[str, Any]] = None,
    alignment_method: str = ALIGNMENT_METHOD,
    bert_lang: str = BERT_LANG,
    bert_model_type: Optional[str] = BERT_MODEL_TYPE,
) -> Dict[str, Any]:
    parsed = parse_review_markdown(review_text)
    grounding = truth_grounding(parsed, pr_entry)
    alignment = review_alignment(
        parsed,
        pr_entry,
        method=alignment_method,
        bert_lang=bert_lang,
        bert_model_type=bert_model_type,
    )

    all_sentences = split_sentences(review_text)
    repetitive = clamp01(
        compute_repetitive_rate(all_sentences, threshold=REPETITIVE_THRESHOLD)
    )
    correctness = issue_correctness(parsed, pr_entry)
    quality = quality_score(grounding, alignment, correctness, repetitive)

    return {
        "truth_grounding": grounding,
        "review_alignment": alignment,
        "issue_correctness": correctness,
        "quality_score": quality,
        "repetitive_rate": repetitive,
        "meta": {
            "sentence_count": len(all_sentences),
            "checklist_item_count": len(parsed.checklist_items),
            "issue_count": len(parsed.issues),
            "review_alignment_method": (
                "bert-score"
                if alignment_method == "bert-score"
                or (alignment_method == "auto" and bert_score_fn is not None)
                else "heuristic"
            ),
            "quality_score_method": "heuristic-proxy",
        },
    }


def load_json(path: Path) -> Dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def load_jsonl_first(path: Path) -> Dict[str, Any]:
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if stripped:
            return json.loads(stripped)
    return {}


def load_pr_entry(path: Path) -> Optional[Dict[str, Any]]:
    if not path.exists():
        raise FileNotFoundError(f"PR file not found: {path}")
    if path.suffix.lower() == ".jsonl":
        return load_jsonl_first(path)
    return load_json(path)


def load_review_text(path: Path) -> str:
    if not path.exists():
        raise FileNotFoundError(f"Review file not found: {path}")
    return path.read_text(encoding="utf-8")


def parse_cli_args(argv: List[str]) -> Tuple[Path, Path]:
    if len(argv) != 3:
        raise SystemExit("Usage: python evaluation.py [input_review] [input_pr]")
    return Path(argv[1]), Path(argv[2])


def main() -> None:
    review_path, pr_path = parse_cli_args(sys.argv)
    review_text = load_review_text(review_path)
    pr_entry = load_pr_entry(pr_path)

    result = evaluate_review(
        review_text,
        pr_entry,
    )
    output_text = json.dumps(result, ensure_ascii=False, indent=2)
    print(output_text)


if __name__ == "__main__":
    main()
