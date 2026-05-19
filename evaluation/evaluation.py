import argparse
import json
import re
from dataclasses import dataclass, field
from difflib import SequenceMatcher
from pathlib import Path
from typing import Any, Dict, List, Optional, Set, Tuple
from bert_score import score as bert_score_fn


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

    precision, recall, f1 = bert_score_fn(cands=cands, refs=refs, **kwargs)
    p = float(precision.mean().item())
    r = float(recall.mean().item())
    f = float(f1.mean().item())
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


def parse_checklist_item_body(body: str) -> Tuple[str, str, List[ChecklistEvidence]]:
    final_answer = ""
    analysis = ""
    evidences: List[ChecklistEvidence] = []

    final_match = re.search(r"Final\s+Answer\s*:\s*(.+)", body, re.IGNORECASE)
    if final_match:
        final_answer = final_match.group(1).strip()

    analysis_match = re.search(r"Analysis\s*:\s*(.+)", body, re.IGNORECASE | re.DOTALL)
    if analysis_match:
        analysis_candidate = analysis_match.group(1).strip()
        stop = re.search(r"\n\s*[-*]\s+", analysis_candidate)
        analysis = (
            analysis_candidate[: stop.start()].strip()
            if stop
            else analysis_candidate.strip()
        )

    triple_colon = re.finditer(
        r"[-*]\s+([^:\n]+?\.java):::([0-9]+\s*[-~]\s*[0-9]+|[0-9]+-[0-9]+):::(.+)",
        body,
        re.IGNORECASE,
    )
    for match in triple_colon:
        evidences.append(
            ChecklistEvidence(
                filepath=match.group(1).strip(),
                lines=match.group(2).replace("~", "-").replace(" ", ""),
                reason=match.group(3).strip(),
            )
        )

    # Supports:
    # - path.java(lines:10-20)
    #   - reason text
    bullet_items = re.finditer(
        r"[-*]\s+([^\n]+?\.java)\s*\(\s*lines?\s*:\s*([^)]+)\)\s*\n\s*[-*]\s+([^\n]+)",
        body,
        re.IGNORECASE,
    )
    for match in bullet_items:
        evidences.append(
            ChecklistEvidence(
                filepath=match.group(1).strip(),
                lines=match.group(2).replace("~", "-").replace(" ", ""),
                reason=match.group(3).strip(),
            )
        )

    if not final_answer:
        paragraphs = [p.strip() for p in re.split(r"\n\s*\n", body) if p.strip()]
        if paragraphs:
            final_answer = paragraphs[0].splitlines()[0].strip()

    if not analysis:
        paragraphs = [p.strip() for p in re.split(r"\n\s*\n", body) if p.strip()]
        if len(paragraphs) > 1:
            analysis = paragraphs[1]
        elif paragraphs:
            analysis = paragraphs[0]

    return final_answer, analysis, evidences


def parse_checklist_items(text: str) -> List[ChecklistItem]:
    start = re.search(r"#\s+Appendix:\s+Detailed\s+Checklist\s+Item\s+Answers", text)
    if not start:
        return []

    scope = text[start.end() :]
    headers = list(re.finditer(r"###\s+Checklist\s+Item:?\s*(.+)", scope))
    if not headers:
        return []

    items: List[ChecklistItem] = []
    for i, header in enumerate(headers):
        body_start = header.end()
        body_end = headers[i + 1].start() if i + 1 < len(headers) else len(scope)
        title = header.group(1).strip()
        body = scope[body_start:body_end].strip()
        final_answer, analysis, evidences = parse_checklist_item_body(body)
        items.append(
            ChecklistItem(
                title=title,
                final_answer=final_answer,
                analysis=analysis,
                evidences=evidences,
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


def structural_correctness(parsed: ParsedReview) -> Dict[str, float]:
    expected_blocks = 1
    found_blocks = 1 if parsed.motivation.strip() else 0
    correct_format_blocks = 1 if parsed.motivation.strip() else 0
    total_blocks = 1 if parsed.motivation.strip() else 0

    for item in parsed.checklist_items:
        expected_blocks += 2

        has_final = bool(item.final_answer.strip())
        has_analysis = bool(item.analysis.strip())

        found_blocks += int(has_final) + int(has_analysis)
        total_blocks += int(has_final) + int(has_analysis)

        correct_format_blocks += int(has_final)
        correct_format_blocks += int(has_analysis and word_count(item.analysis) >= 10)

        evidence_line_count = len(
            [
                line
                for line in split_sentences(item.analysis)
                if ".java" in line and "line" in line.lower()
            ]
        )
        expected_evidence_blocks = max(len(item.evidences), evidence_line_count)
        expected_blocks += expected_evidence_blocks

        found_blocks += len(item.evidences)
        total_blocks += len(item.evidences)

        for evidence in item.evidences:
            path_ok = evidence.filepath.endswith(".java")
            lines_ok = bool(normalize_line_span(evidence.lines))
            reason_ok = word_count(evidence.reason) >= 10
            if path_ok and lines_ok and reason_ok:
                correct_format_blocks += 1

    output_completeness = clamp01(safe_div(found_blocks, expected_blocks))
    format_correctness = clamp01(safe_div(correct_format_blocks, max(1, total_blocks)))

    return {
        "output_completeness": output_completeness,
        "format_correctness": format_correctness,
    }


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
    comments = pr_entry.get("comments") or []
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
        pr_entry.get("pr_description") or pr_entry.get("description") or ""
    )


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

    use_bert = method == "bert-score" or (
        method == "auto" and bert_score_fn is not None
    )
    if use_bert:
        cp, cr, cf1 = bert_sentence_alignment(
            comment_refs,
            comment_cands,
            lang=bert_lang,
            model_type=bert_model_type,
        )
        ip, ir, if1 = bert_sentence_alignment(
            interp_refs,
            interp_cands,
            lang=bert_lang,
            model_type=bert_model_type,
        )
    else:
        cp, cr, cf1 = greedy_sentence_alignment(comment_refs, comment_cands)
        ip, ir, if1 = greedy_sentence_alignment(interp_refs, interp_cands)

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
    return paths


def issue_correctness(
    parsed: ParsedReview, pr_entry: Optional[Dict[str, Any]]
) -> Dict[str, float]:
    issues = parsed.issues
    if not issues:
        return {"issue_correctness": 0.0, "valid_issues": 0.0, "total_issues": 0.0}

    changed_paths = extract_changed_file_paths(pr_entry)
    valid_count = 0
    for issue in issues:
        has_basic_fields = all(
            [
                issue.issue_type.strip(),
                issue.title.strip(),
                issue.location.strip(),
                issue.detail.strip(),
            ]
        )
        has_location_reference = (
            any(path in issue.location for path in changed_paths)
            if changed_paths
            else True
        )
        detail_long_enough = word_count(issue.detail) >= 8
        if has_basic_fields and has_location_reference and detail_long_enough:
            valid_count += 1

    return {
        "issue_correctness": clamp01(safe_div(valid_count, len(issues))),
        "valid_issues": float(valid_count),
        "total_issues": float(len(issues)),
    }


def quality_score(
    structural: Dict[str, float],
    grounding: Dict[str, float],
    alignment: Dict[str, float],
    repetitive_rate: float,
    review_text: str,
) -> Dict[str, float]:
    comprehensiveness = (
        structural["output_completeness"]
        + grounding["coverage_score"]
        + alignment["comment_recall"]
    ) / 3.0

    total_words = word_count(review_text)
    if total_words <= 250:
        verbosity_penalty = 0.25
    elif total_words >= 2200:
        verbosity_penalty = 0.2
    else:
        verbosity_penalty = 0.0

    conciseness = clamp01(1.0 - repetitive_rate - verbosity_penalty)
    relevance = (
        grounding["grounding_score"]
        + alignment["comment_precision"]
        + alignment["interpretation_precision"]
    ) / 3.0

    return {
        "comprehensiveness": clamp01(comprehensiveness),
        "conciseness": clamp01(conciseness),
        "relevance": clamp01(relevance),
    }


def evaluate_review(
    review_text: str,
    pr_entry: Optional[Dict[str, Any]] = None,
    alignment_method: str = "auto",
    bert_lang: str = "en",
    bert_model_type: Optional[str] = None,
) -> Dict[str, Any]:
    parsed = parse_review_markdown(review_text)
    structural = structural_correctness(parsed)
    grounding = truth_grounding(parsed, pr_entry)
    alignment = review_alignment(
        parsed,
        pr_entry,
        method=alignment_method,
        bert_lang=bert_lang,
        bert_model_type=bert_model_type,
    )

    all_sentences = split_sentences(review_text)
    repetitive = clamp01(compute_repetitive_rate(all_sentences))
    correctness = issue_correctness(parsed, pr_entry)
    quality = quality_score(structural, grounding, alignment, repetitive, review_text)

    return {
        "structural_correctness": structural,
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
        },
    }


def load_pr_entry(
    pr_json: Optional[Path], pr_jsonl: Optional[Path], pr_id: Optional[int]
) -> Optional[Dict[str, Any]]:
    if pr_json:
        return json.loads(pr_json.read_text(encoding="utf-8"))

    if pr_jsonl:
        lines = [
            line.strip()
            for line in pr_jsonl.read_text(encoding="utf-8").splitlines()
            if line.strip()
        ]
        entries = [json.loads(line) for line in lines]
        if pr_id is None:
            return entries[0] if entries else None
        for entry in entries:
            if int(entry.get("pr_id", -1)) == pr_id:
                return entry
        return None

    return None


def build_arg_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Evaluate LLM code review quality.")
    parser.add_argument(
        "--review", required=True, type=Path, help="Path to review markdown file."
    )
    parser.add_argument(
        "--pr-json", type=Path, default=None, help="Path to one PR json file."
    )
    parser.add_argument(
        "--pr-jsonl", type=Path, default=None, help="Path to PR dataset jsonl file."
    )
    parser.add_argument(
        "--pr-id", type=int, default=None, help="PR id used with --pr-jsonl."
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=None,
        help="Path to write evaluation result as JSON.",
    )
    parser.add_argument(
        "--alignment-method",
        choices=["auto", "heuristic", "bert-score"],
        default="auto",
        help="Method for review alignment metric.",
    )
    parser.add_argument(
        "--bert-lang",
        default="en",
        help="Language hint passed to bert_score when enabled.",
    )
    parser.add_argument(
        "--bert-model-type",
        default=None,
        help="Optional model_type for bert_score (e.g. roberta-large).",
    )
    return parser


def main() -> None:
    parser = build_arg_parser()
    args = parser.parse_args()

    review_path: Path = args.review
    if not review_path.exists():
        raise FileNotFoundError(f"Review file not found: {review_path}")

    review_text = review_path.read_text(encoding="utf-8")
    pr_entry = load_pr_entry(args.pr_json, args.pr_jsonl, args.pr_id)

    result = evaluate_review(
        review_text,
        pr_entry,
        alignment_method=args.alignment_method,
        bert_lang=args.bert_lang,
        bert_model_type=args.bert_model_type,
    )
    output_text = json.dumps(result, ensure_ascii=False, indent=2)

    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(output_text, encoding="utf-8")
    else:
        print(output_text)


if __name__ == "__main__":
    main()
