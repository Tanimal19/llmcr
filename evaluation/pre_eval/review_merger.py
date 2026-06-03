# merge all review.json into one jsonl
import json
from pathlib import Path
from dacite import from_dict
from share.utils import camel_to_snake
from share.code_review_scheme import CodeReviewEntry
from config import (
    REVIEW_SOURCE_DIR,
    MERGED_REVIEW_JSONL,
    EvaluationGroup,
)

GROUP = EvaluationGroup(input("Enter evaluation group: "))
SOURCE_DIR = REVIEW_SOURCE_DIR(GROUP.value)
OUTPUT_PATH = MERGED_REVIEW_JSONL(GROUP.value)


count = 0
for review_file in SOURCE_DIR.glob("*/review.json"):
    print(f"Processing {review_file}...")
    with review_file.open(encoding="utf-8") as f:
        review_data = json.load(f)
        review_data = camel_to_snake(review_data)  # convert keys to snake_case
        if not isinstance(review_data, dict):
            print(f"Skipping {review_file}: not a JSON object")
            continue

        # to check if the review_data is correct
        try:
            review_entry = from_dict(CodeReviewEntry, review_data)
        except Exception as e:
            print(f"Error parsing {review_file}: {e}")
            continue

        # Write to output file in JSONL format
        with OUTPUT_PATH.open("a", encoding="utf-8") as out_f:
            json.dump(review_data, out_f)
            out_f.write("\n")
            count += 1

print(f"Merged {count} reviews written to {OUTPUT_PATH}")
