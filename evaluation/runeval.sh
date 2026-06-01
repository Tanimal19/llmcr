#!/usr/bin/env bash
set -u

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

shopt -s nullglob
review_files=(reviews/llmcr/*/review.json)

if [ ${#review_files[@]} -eq 0 ]; then
	echo "No review files found under reviews/*/review.json"
	exit 1
fi

ok_count=0
fail_count=0

for review_file in "${review_files[@]}"; do
	echo "Running evaluation for: $review_file"
	if python main.py "$review_file" exports/eval.jsonl; then
		ok_count=$((ok_count + 1))
	else
		fail_count=$((fail_count + 1))
		echo "Failed: $review_file" >&2
	fi
done

echo "Done. success=$ok_count failed=$fail_count total=${#review_files[@]}"

if [ $fail_count -gt 0 ]; then
	exit 1
fi
