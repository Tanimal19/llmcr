REVIEW_EVA_PROMPT = """You are an expert Java software engineer with extensive experience conducting professional code reviews. Your task is to evaluate the relevance of a code review with respect to a given Java code change.

A review is considered relevant if it meets both of the following criteria:
1. Comprehensiveness: 
    - Correctly identifies and discusses the key functional, structural, performance, readability, maintainability, or correctness implications of the code change.
    - Mentions important issues, risks, or improvements that a competent reviewer would reasonably be expected to notice.
    - Does not omit major concerns that are clearly present in the change.
2. Conciseness
    - Stays strictly focused on the provided code change.
    - Avoids irrelevant commentary, generic advice, or restating obvious code behavior without analysis.
	•	Uses minimal wording while still conveying necessary insights.

A relevant review balances these two aspects: it is thorough without being verbose, and focused without being superficial.

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

INTERPRETATION_EVA_PROMPT = """You are a highly skilled software engineer who has a lot of experience reviewing code changes. Your task is to evaluate the relevance of any given code change explanation.

A relevant explanation is one that effectively clarifies the purpose, functionality, and implications of the code change. It should provide insights into why the change was made and how it affects the existing codebase.

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

QUERY_ANSWER_EVA_PROMPT = """You are an expert software engineer with extensive experience in Spring AI project. Your task is to evaluate the relevance of answers provided to questions about the project.

A relevant answer is one that directly addresses the question posed, providing clear, accurate, and concise information. It should demonstrate a thorough understanding of the codebase and the specific context of the question.

Now look at the question and answer below and score the relevance of the answer on a scale of 1 to 5.

Question: <query>

Answer: <answer>

You should only respond with a single integer number between 1 and 5.
Relevance score (1-5):
"""

from dataclasses import dataclass
from google import genai
from dotenv import load_dotenv
import json


@dataclass
class Document:
    source_name: str
    embedding_ids: list[int]
    similarity_score: float


@dataclass
class TaskResult:
    task: str  # review | interpretation | query-answer
    group: str  # adaptive-enriched | simple-enriched | adaptive-plain
    reponse: str
    documents: list[Document]
    query: str  # for query_answer task
    pr_id: str  # for review and interpretation tasks
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
            documents = item.get("documents", [])
            item["documents"] = [
                Document(
                    source_name=doc["source_name"],
                    embedding_ids=doc["embedding_ids"],
                    similarity_score=doc["similarity_score"],
                )
                for doc in documents
            ]

            result = TaskResult(
                task=item["task"],
                group=item["group"],
                reponse=item["response"],
                documents=item["documents"],
                query=item.get("query", "null"),
                pr_id=item.get("pr_id", "null"),
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
    results = load_inference_results("./data/results.json")
    prs = load_pull_requests("./data/pull_requests.json")

    # run evaluation for each result
    # client = genai.Client(api_key="")
    # for result in results:
    #     if result.task == "review":
    #         prompt = REVIEW_EVA_PROMPT
    #         prompt = prompt.replace("<hunks>", "\n".join(prs[result.pr_id].hunks))
    #         prompt = prompt.replace("<review>", result.reponse)
    #     elif result.task == "interpretation":
    #         prompt = INTERPRETATION_EVA_PROMPT
    #         prompt = prompt.replace("<hunks>", "\n".join(prs[result.pr_id].hunks))
    #         prompt = prompt.replace("<explanation>", result.reponse)
    #     elif result.task == "query-answer":
    #         prompt = QUERY_ANSWER_EVA_PROMPT
    #         prompt = prompt.replace("<query>", result.query)
    #         prompt = prompt.replace("<answer>", result.reponse)
    #     else:
    #         print("Unknown task:", result.task)
    #         continue

    #     print("=" * 20)
    #     print("task:", result.task, "group:", result.group)
    #     print(prompt)

    #     response = client.models.generate_content(
    #         model="gemini-2.5-flash-lite",
    #         contents=prompt,
    #     )

    #     print("Model response:", response.text.strip())
    #     try:
    #         result.score = int(response.text.strip())
    #     except ValueError:
    #         result.score = -1  # invalid score
    #     print("Assigned score:", result.score)

    # Per-item analysis: analyze each pull request and each query individually
    from collections import defaultdict

    # Group results by task and identifier (pr_id or query), then by group
    # Structure: task -> identifier -> group -> result
    grouped_results = defaultdict(lambda: defaultdict(dict))

    for result in results:
        task = result.task
        group = result.group
        if task in ["review", "interpretation"]:
            identifier = result.pr_id
        elif task == "query-answer":
            identifier = result.query
        else:
            identifier = "unknown"
        grouped_results[task][identifier][group] = result

    # Print per-item analysis - comparing groups within each task
    print("\n" + "=" * 80)
    print("PER-ITEM DOCUMENT ANALYSIS - COMPARING GROUPS")
    print("=" * 80)

    for task in sorted(grouped_results.keys()):
        print(f"\n{'=' * 80}")
        print(f"Task: {task}")
        print("=" * 80)

        for identifier in sorted(grouped_results[task].keys()):
            if task in ["review", "interpretation"]:
                print(f"\n{'-' * 80}")
                print(f"Pull Request ID: {identifier}")
                print("-" * 80)
            elif task == "query-answer":
                print(f"\n{'-' * 80}")
                print(f"Query: {identifier}")
                print("-" * 80)

            # Compare all groups for this specific item
            group_results = grouped_results[task][identifier]

            for group in sorted(group_results.keys()):
                result = group_results[group]
                print(f"\n  [{group}]")
                print(f"    Documents retrieved: {len(result.documents)}")

                if result.documents:
                    # Calculate statistics for this specific item/group
                    scores = [doc.similarity_score for doc in result.documents]
                    avg_score = sum(scores) / len(scores)
                    min_score = min(scores)
                    max_score = max(scores)

                    print(f"    Similarity scores:")
                    print(f"      Average: {avg_score:.4f}")
                    print(f"      Min: {min_score:.4f}")
                    print(f"      Max: {max_score:.4f}")

                    print(f"\n    Document details:")
                    for i, doc in enumerate(result.documents, 1):
                        print(f"      [{i}] {doc.source_name}")
                        print(f"          Similarity: {doc.similarity_score:.4f}")
                        print(f"          Embedding IDs: {doc.embedding_ids}")
                else:
                    print("    No documents retrieved")

            print()  # Extra line between items

    # comparing groups within each task
    print("\n" + "=" * 80)
    print("SUMMARY STATISTICS - COMPARING GROUPS WITHIN EACH TASK")
    print("=" * 80)

    for task in sorted(grouped_results.keys()):
        print(f"\n{'=' * 80}")
        print(f"Task: {task}")
        print("=" * 80)

        # Collect all groups for this task
        all_groups = set()
        for identifier in grouped_results[task].values():
            all_groups.update(identifier.keys())

        # Calculate statistics for each group
        group_stats = {}
        for group in sorted(all_groups):
            all_scores = []
            all_doc_counts = []
            unique_docs = set()

            for identifier, group_results in grouped_results[task].items():
                if group in group_results:
                    result = group_results[group]
                    all_doc_counts.append(len(result.documents))
                    for doc in result.documents:
                        all_scores.append(doc.similarity_score)
                        unique_docs.add(doc.source_name)

            num_items = sum(
                1
                for id_results in grouped_results[task].values()
                if group in id_results
            )
            total_retrievals = sum(all_doc_counts)
            avg_docs_per_item = total_retrievals / num_items if num_items > 0 else 0

            group_stats[group] = {
                "num_items": num_items,
                "total_retrievals": total_retrievals,
                "avg_docs_per_item": avg_docs_per_item,
                "unique_docs": len(unique_docs),
                "avg_score": sum(all_scores) / len(all_scores) if all_scores else 0,
                "min_score": min(all_scores) if all_scores else 0,
                "max_score": max(all_scores) if all_scores else 0,
            }

        # Display comparison
        print("\nGroup Comparison:")
        print(f"{'Metric':<30} " + " ".join(f"{g:>25}" for g in sorted(all_groups)))
        print("-" * 80)

        metrics = [
            ("Total items", "num_items", "{}"),
            ("Total doc retrievals", "total_retrievals", "{}"),
            ("Avg docs per item", "avg_docs_per_item", "{:.2f}"),
            ("Unique documents", "unique_docs", "{}"),
            ("Avg similarity", "avg_score", "{:.4f}"),
            ("Min similarity", "min_score", "{:.4f}"),
            ("Max similarity", "max_score", "{:.4f}"),
        ]

        for metric_name, metric_key, fmt in metrics:
            values = [
                fmt.format(group_stats[g][metric_key]) for g in sorted(all_groups)
            ]
            print(f"{metric_name:<30} " + " ".join(f"{v:>25}" for v in values))
            print(f"      Max: {max_score:.4f}")

    print("\n" + "=" * 80)
