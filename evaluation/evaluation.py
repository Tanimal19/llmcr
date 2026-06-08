import time
from functools import lru_cache
from dataclasses import dataclass, field, asdict
from typing import Any, Dict
from dacite import from_dict

from evaluator.grounding_evaluator import GroundingEvaluator
from evaluator.alignment_evaluator import AlignmentEvaluator
from evaluator.laaj_evaluator import LaajEvaluator
from evaluator.repetitive_evaluator import RepetitiveEvaluator

from share.utils import iter_jsonl_entries, append_jsonl_entry
from share.code_review_scheme import CodeReviewEntry
from share.pull_request_scheme import PullRequestEntry

from config import (
    EvaluationGroup,
    REPHRASED_PULL_REQUEST_JSONL,
    MERGED_REVIEW_JSONL,
    EVALUATION_RESULT_JSONL,
)

EVALUATORS = [
    GroundingEvaluator(),
    AlignmentEvaluator(),
    LaajEvaluator(),
    RepetitiveEvaluator(),
]


@dataclass
class EvaluationResult:
    pr_id: int
    group: str
    review: CodeReviewEntry
    pull_request: PullRequestEntry
    results: Dict[str, Any] = field(default_factory=dict)


@lru_cache(maxsize=1)
def load_pull_request_mapping():
    pr_mapping = {}
    for pr_entry in iter_jsonl_entries(REPHRASED_PULL_REQUEST_JSONL):
        pr_obj = from_dict(PullRequestEntry, pr_entry)
        pr_mapping[pr_obj.id] = pr_obj
    return pr_mapping


def convert_review_data(data: dict, group: EvaluationGroup) -> CodeReviewEntry:
    if group == EvaluationGroup.CODERABBIT:
        return CodeReviewEntry.from_coderabbit_format(data)
    elif group == EvaluationGroup.COPILOT:
        return CodeReviewEntry.from_copilot_format(data)
    elif group == EvaluationGroup.LLMCR or group == EvaluationGroup.SINGLELLM:
        return CodeReviewEntry.from_llmcr_format(data)
    else:
        print(f"Warning: Unrecognized evaluation group {group}, using default parsing")
        return from_dict(CodeReviewEntry, data)


def merge_partial_data(existing: Any, new_value: Any) -> Any:
    if isinstance(existing, dict) and isinstance(new_value, dict):
        merged = dict(existing)
        for key, value in new_value.items():
            if key in merged:
                merged[key] = merge_partial_data(merged[key], value)
            else:
                merged[key] = value
        return merged
    return new_value


def load_existing_results(output_jsonl) -> Dict[int, dict]:
    existing_results: Dict[int, dict] = {}
    if not output_jsonl.exists():
        return existing_results

    for row in iter_jsonl_entries(output_jsonl):
        pr_id = row.get("pr_id")
        if isinstance(pr_id, int):
            existing_results[pr_id] = row
    return existing_results


def main() -> None:
    _PR_MAPPING_CACHE = load_pull_request_mapping()

    start_time = time.time()

    for group in EvaluationGroup:
        print(f"Evaluating group: {group.value}")

        review_jsonl = MERGED_REVIEW_JSONL(group.value)
        output_jsonl = EVALUATION_RESULT_JSONL(group.value)

        if not review_jsonl.exists():
            print(
                f"Warning: review JSONL file not found for group {group.value}, skipping..."
            )
            continue

        merged_results = load_existing_results(output_jsonl)

        for review_data in iter_jsonl_entries(review_jsonl):
            review_obj = convert_review_data(review_data, group)
            pr_entry = _PR_MAPPING_CACHE.get(review_obj.pr_id)
            if pr_entry is None:
                print(f"Warning: PR entry not found for PR ID {review_obj.pr_id}")
                continue

            evaluation_result = dict()
            for evaluator in EVALUATORS:
                print(
                    f"Evaluating PR ID {review_obj.pr_id} with {evaluator.__class__.__name__}..."
                )
                evaluation_result[evaluator.__class__.__name__] = evaluator.evaluate(
                    review_obj, pr_entry
                )

            result = EvaluationResult(
                pr_id=review_obj.pr_id,
                group=group.value,
                review=review_obj,
                pull_request=pr_entry,
                results=evaluation_result,
            )

            new_row = asdict(result)
            merged_results[review_obj.pr_id] = merge_partial_data(
                merged_results.get(review_obj.pr_id, {}), new_row
            )

            del result  # free memory

        with output_jsonl.open("w", encoding="utf-8") as file:
            for row in merged_results.values():
                append_jsonl_entry(file, row)

    end_time = time.time()
    print(f"Evaluation completed in {end_time - start_time:.2f} seconds.")


if __name__ == "__main__":
    main()
