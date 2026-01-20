REVIEW_EVA_PROMPT = """You are a highly skilled software engineer who has a lot of experience reviewing code changes. Your task is to rate the relevance of any given code change.

You will be asked to rate the relevance of reviews for given Java code changes. A relevant review is one which is both concise and comprehensive. A concise review contains very little text not related to the code change. A comprehensive review contains all the information about a code change that should be covered by a review. A relevant review is comprehensive while being concise.

Now look at the Java code change and review below and score the relevance of the review on a scale of 1 to 5.

Code change hunks at below.
-----------------
<hunks>
-----------------

Code review at below.
-----------------
<review>
-----------------

You should only respond with a single integer number between 1 and 5.
Relevance score (1-5):
"""

INTERPRETATION_EVA_PROMPT = """You are a highly skilled software engineer who has a lot of experience reviewing code changes. Your task is to rate the relevance of any given code change explanation.

You will be asked to rate the relevance of explanations for given Java code changes. A relevant explanation is one which is both concise and comprehensive. A concise explanation contains very little text not related to the code change. A comprehensive explanation contains all the information about a code change that should be covered by an explanation. A relevant explanation is comprehensive while being concise.

Now look at the Java code change and explanation below and score the relevance of the explanation on a scale of 1 to 5.

Code change hunks at below.
-----------------
<hunks>
-----------------

Explanation at below.
-----------------
<explanation>
-----------------

You should only respond with a single integer number between 1 and 5.
Relevance score (1-5):
"""

from dataclasses import dataclass
import json


@dataclass
class TaskResult:
    pr_id: str
    task: str  # code_review | code_interpretation
    group: str  # adaptive-enriched | simple-enriched | adaptive-plain
    reponse: str
    documents: list[int]
    eva_response: str | None = None
    score: int | None = None


@dataclass
class PullRequest:
    pr_id: str
    hunks: list[str]


def load_inference_results(file_path: str) -> list[TaskResult]:
    results = []
    with open(file_path, "r") as f:
        data = json.load(f)
        for item in data:
            result = TaskResult(
                pr_id=str(item["pr_id"]),
                task=item["task"],
                group=item["group"],
                reponse=item["response"],
                documents=item["documents"],
            )
            results.append(result)
    return results


def load_pull_requests(file_path: str) -> dict[str, PullRequest]:
    prs = {}
    with open(file_path, "r") as f:
        data = json.load(f)
        for item in data:
            pr = PullRequest(
                pr_id=str(item["id"]),
                hunks=[str(hunk) for hunk in item["hunks"]],
            )
            prs[pr.pr_id] = pr
    return prs


if __name__ == "__main__":
    results = load_inference_results("results.json")
    prs = load_pull_requests("pull_requests.json")

    # run evaluation for each result
    for result in results:
        if result.task == "code_review":
            prompt = REVIEW_EVA_PROMPT
            prompt = prompt.replace("<hunks>", "\n".join(prs[result.pr_id].hunks))
            prompt = prompt.replace("<review>", result.reponse)
        elif result.task == "code_interpretation":
            prompt = INTERPRETATION_EVA_PROMPT
            prompt = prompt.replace("<hunks>", "\n".join(prs[result.pr_id].hunks))
            prompt = prompt.replace("<explanation>", result.reponse)
        else:
            continue

        print(prompt)

        # Here you would integrate with your LLM to get the evaluation response
        # For demonstration, we'll just set a dummy response and score
        result.eva_response = "Dummy evaluation response"
        result.score = 5  # Dummy score

    # Calculate average scores per group and task
    from collections import defaultdict

    scores_by_group_task = defaultdict(list)
    for result in results:
        if result.score is not None:
            key = (result.group, result.task)
            scores_by_group_task[key].append(result.score)

    # Print table of average scores
    print("\nAverage Scores by Group and Task:")
    print("-" * 80)
    print(f"{'Group':<30} {'Task':<30} {'Avg Score':<10} {'Count':<10}")
    print("-" * 80)
    for (group, task), scores in sorted(scores_by_group_task.items()):
        avg_score = sum(scores) / len(scores) if scores else 0
        print(f"{group:<30} {task:<30} {avg_score:<10.2f} {len(scores):<10}")
    print("-" * 80)

    # Analyze document IDs distribution per pr_id between groups
    print("\n\nDocument IDs Distribution Analysis per PR:")
    print("=" * 120)

    # Group results by PR ID and task
    pr_task_results = defaultdict(lambda: defaultdict(dict))
    for result in results:
        pr_task_results[result.pr_id][result.task][result.group] = set(result.documents)

    # Analyze each PR and task combination
    for pr_id in sorted(pr_task_results.keys()):
        for task in sorted(pr_task_results[pr_id].keys()):
            print(f"\nPR: {pr_id} | Task: {task}")
            print("-" * 120)

            groups_data = pr_task_results[pr_id][task]

            # Get document sets for each group
            ae_docs = groups_data.get("adaptive-enriched", set())
            se_docs = groups_data.get("simple-enriched", set())
            ap_docs = groups_data.get("adaptive-plain", set())

            # Print retrieved documents for each group
            print(
                f"  adaptive-enriched:  {sorted(ae_docs) if ae_docs else '[]'} (count: {len(ae_docs)})"
            )
            print(
                f"  simple-enriched:    {sorted(se_docs) if se_docs else '[]'} (count: {len(se_docs)})"
            )
            print(
                f"  adaptive-plain:     {sorted(ap_docs) if ap_docs else '[]'} (count: {len(ap_docs)})"
            )

            # Calculate overlaps and differences
            all_docs = ae_docs | se_docs | ap_docs
            common_all = ae_docs & se_docs & ap_docs

            print(
                f"\n  Common to all groups:     {sorted(common_all) if common_all else '[]'} (count: {len(common_all)})"
            )

            # Unique to each group
            ae_unique = ae_docs - se_docs - ap_docs
            se_unique = se_docs - ae_docs - ap_docs
            ap_unique = ap_docs - ae_docs - se_docs

            if ae_unique or se_unique or ap_unique:
                print(
                    f"  Unique to adaptive-enriched:  {sorted(ae_unique) if ae_unique else '[]'} (count: {len(ae_unique)})"
                )
                print(
                    f"  Unique to simple-enriched:    {sorted(se_unique) if se_unique else '[]'} (count: {len(se_unique)})"
                )
                print(
                    f"  Unique to adaptive-plain:     {sorted(ap_unique) if ap_unique else '[]'} (count: {len(ap_unique)})"
                )

            # Pairwise overlaps (excluding common to all)
            ae_se_only = (ae_docs & se_docs) - ap_docs - common_all
            ae_ap_only = (ae_docs & ap_docs) - se_docs - common_all
            se_ap_only = (se_docs & ap_docs) - ae_docs - common_all

            if ae_se_only or ae_ap_only or se_ap_only:
                print(
                    f"  Common to adaptive-enriched & simple-enriched only: {sorted(ae_se_only) if ae_se_only else '[]'}"
                )
                print(
                    f"  Common to adaptive-enriched & adaptive-plain only:  {sorted(ae_ap_only) if ae_ap_only else '[]'}"
                )
                print(
                    f"  Common to simple-enriched & adaptive-plain only:    {sorted(se_ap_only) if se_ap_only else '[]'}"
                )

    print("\n" + "=" * 120)

    # Overall statistics
    print("\n\nOverall Document Retrieval Statistics:")
    print("-" * 80)
    print(f"{'Group':<30} {'Avg Docs':<12} {'Min':<8} {'Max':<8} {'Total Results':<15}")
    print("-" * 80)
    for group in ["adaptive-enriched", "simple-enriched", "adaptive-plain"]:
        all_docs = [len(r.documents) for r in results if r.group == group]
        if all_docs:
            avg_docs = sum(all_docs) / len(all_docs)
            min_docs = min(all_docs)
            max_docs = max(all_docs)
            print(
                f"{group:<30} {avg_docs:<12.2f} {min_docs:<8} {max_docs:<8} {len(all_docs):<15}"
            )
    print("-" * 80)
