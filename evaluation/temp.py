import argparse
import json
from pathlib import Path


def read_jsonl(path: Path):
    rows = []
    with path.open("r", encoding="utf-8") as f:
        for line_no, line in enumerate(f, 1):
            line = line.strip()
            if not line:
                continue
            try:
                rows.append(json.loads(line))
            except json.JSONDecodeError as exc:
                raise ValueError(f"Invalid JSON at {path}:{line_no}: {exc}") from exc
    return rows


def build_static_analysis_map(merged_rows):
    results = {}
    for row in merged_rows:
        pr_id = row.get("pr_id")
        if pr_id is None:
            continue
        if "static_analysis_results" in row:
            results[pr_id] = row.get("static_analysis_results")
    return results


def update_pr_rows(pr_rows, static_map):
    updated = []
    hit = 0
    for row in pr_rows:
        pr_id = row.get("pr_id", row.get("id"))
        if pr_id in static_map:
            row["static_analysis_results"] = static_map[pr_id]
            hit += 1
        updated.append(row)
    return updated, hit


def write_jsonl(path: Path, rows):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as f:
        for row in rows:
            f.write(json.dumps(row, ensure_ascii=False) + "\n")


def resolve_default_input_path(base_dir: Path):
    candidates = [
        base_dir / "data/exports/repharsed_pull_request.jsonl",
        base_dir / "data/exports/rephrased_pull_request.jsonl",
    ]
    for candidate in candidates:
        if candidate.exists():
            return candidate
    return candidates[-1]


def main():
    base_dir = Path(__file__).resolve().parent
    default_input = resolve_default_input_path(base_dir)
    default_merged = base_dir / "data/reviews/llmcr/merged_review.jsonl"
    default_output = (
        base_dir / "data/exports/rephrased_pull_request.with_static_analysis.jsonl"
    )

    parser = argparse.ArgumentParser(
        description="Inject static_analysis_results into rephrased pull request JSONL by PR id"
    )
    parser.add_argument(
        "--input", type=Path, default=default_input, help="Path to pull request JSONL"
    )
    parser.add_argument(
        "--merged-review",
        type=Path,
        default=default_merged,
        help="Path to merged_review JSONL",
    )
    parser.add_argument(
        "--output", type=Path, default=default_output, help="Path to output JSONL"
    )

    args = parser.parse_args()

    if not args.input.exists():
        raise FileNotFoundError(f"Input JSONL not found: {args.input}")
    if not args.merged_review.exists():
        raise FileNotFoundError(f"Merged review JSONL not found: {args.merged_review}")

    pr_rows = read_jsonl(args.input)
    merged_rows = read_jsonl(args.merged_review)

    static_map = build_static_analysis_map(merged_rows)
    updated_rows, matched_count = update_pr_rows(pr_rows, static_map)
    write_jsonl(args.output, updated_rows)

    print(f"Input rows: {len(pr_rows)}")
    print(f"Merged rows: {len(merged_rows)}")
    print(f"Matched by PR id: {matched_count}")
    print(f"Output written to: {args.output}")


if __name__ == "__main__":
    main()
