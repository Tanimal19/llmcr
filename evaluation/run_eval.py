"""
Run batch reviews: for each entry in exports/eval.jsonl,
write it as a standalone JSON file and invoke spring-app/run.sh review <path>.
"""

import json
import subprocess
import sys
from pathlib import Path

EVAL_JSONL = Path(__file__).parent / "exports" / "eval.jsonl"
PR_OUTPUT_DIR = Path(__file__).parent / "exports" / "prs"
SPRING_APP_DIR = Path(__file__).parent.parent / "spring-app"
RUN_SH = SPRING_APP_DIR / "run.sh"


def load_entries(jsonl_path: Path):
    entries = []
    with jsonl_path.open(encoding="utf-8") as f:
        for lineno, line in enumerate(f, 1):
            line = line.strip()
            if not line:
                continue
            try:
                entries.append(json.loads(line))
            except json.JSONDecodeError as exc:
                print(f"[WARN] Skipping line {lineno}: {exc}", file=sys.stderr)
    return entries


def save_entry(entry: dict, output_dir: Path) -> Path:
    pr_id = entry.get("pr_id", "unknown")
    output_dir.mkdir(parents=True, exist_ok=True)
    out_path = output_dir / f"pr_{pr_id}.json"
    out_path.write_text(
        json.dumps(entry, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    return out_path


def run_review(pr_json_path: Path) -> int:
    cmd = ["bash", str(RUN_SH), "review", str(pr_json_path.resolve())]
    print(f"[RUN] {' '.join(cmd)}")
    result = subprocess.run(cmd, cwd=SPRING_APP_DIR)
    return result.returncode


def main():
    if not EVAL_JSONL.exists():
        print(f"Error: {EVAL_JSONL} not found.", file=sys.stderr)
        sys.exit(1)

    entries = load_entries(EVAL_JSONL)
    print(f"Loaded {len(entries)} entries from {EVAL_JSONL}")

    failed = []
    for i, entry in enumerate(entries, 1):
        pr_id = entry.get("pr_id", f"index_{i}")
        print(f"\n[{i}/{len(entries)}] PR {pr_id}")

        pr_json_path = save_entry(entry, PR_OUTPUT_DIR)
        print(f"  Saved PR JSON → {pr_json_path}")

        rc = run_review(pr_json_path)
        if rc != 0:
            print(
                f"  [WARN] run.sh exited with code {rc} for PR {pr_id}", file=sys.stderr
            )
            failed.append(pr_id)

    print(f"\nDone. {len(entries) - len(failed)}/{len(entries)} succeeded.")
    if failed:
        print(f"Failed PR IDs: {failed}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
