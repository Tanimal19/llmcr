from dataclasses import dataclass
from google import genai
from dotenv import load_dotenv
import json
import os

QUERY_ANSWER_EVALUATION_PROMPT = """You are an expert software engineer with extensive experience in Spring AI project (https://github.com/spring-projects/spring-ai). Your task is to evaluate the quality of answers provided to questions about the project. A good answer should be relevant, accurate, and helpful in addressing the question asked. A bad answer provide unecessary, incorrect, or misleading information. 

<qa_pair>

Now look at the given question-answer pairs and give quality scores for each pair on a scale of 1 to 5. You should give a short reason less than 50 words for each determined score. You should output with this format:
{pair_id}::{score}::{reason}
"""

CODE_INTERPRETATION_EVALUATION_PROMPT = """You are an expert software engineer with extensive experience in Spring AI project (https://github.com/spring-projects/spring-ai). Your will be given a list of pull request descriptions written by others. Your task is to evaluate the quality of given pull request descriptions by referring to the original pull request details.

A code change description is a public record of change, and it is important that it communicates:
1. What change is being made? This should summarize the major changes such that readers have a sense of what is being changed without needing to read the entire CL. The description should be concise and to the point.
2. Why are these changes being made? What contexts did you have as an author when making this change? Were there decisions you made that aren't reflected in the source code? etc.

Original pull request details:
---------------
<pull_request>
---------------

Descriptions to be evaluated:
---------------
<generated_descriptions>
---------------

Now look at the original pull request and a list of pull request descriptions, and give quality scores for each description on a scale of 1 to 5. You should give a short reason less than 50 words for each determined score. You should output with this format:
{pair_id}::{score}::{reason}
"""


@dataclass
class Document:
    source_name: str
    embedding_ids: list[int]
    similarity_score: float


@dataclass
class QATaskResult:
    group: str
    query: str
    documents: list[Document]
    response: str
    score: int | None = None


@dataclass
class CodeInterpreationTaskResult:
    group: str
    pr_id: int
    pr_title: str
    pr_description: str
    pr_hunks: list[str]
    documents: list[Document]
    response: str
    score: int | None = None


def load_qa_results(file_path: str) -> list[QATaskResult]:
    results = []
    with open(file_path, "r") as f:
        data = json.load(f)
        for item in data:
            if item.get("task", "") != "query_answer":
                continue

            documents = [
                Document(
                    source_name=doc["source_name"],
                    embedding_ids=doc["embedding_ids"],
                    similarity_score=doc["similarity_score"],
                )
                for doc in item.get("documents", [])
            ]

            results.append(
                QATaskResult(
                    group=item["group"],
                    query=item["input"],
                    documents=documents,
                    response=item["response"],
                )
            )
    return results


def generate_qa_evaluation_prompt(qa_results: list[QATaskResult]) -> str:
    qa_pairs = []
    for id, result in enumerate(qa_results):
        qa_pair = {
            "pair_id": id,
            "question": result.query,
            "answer": result.response,
        }
        qa_pairs.append(qa_pair)

    prompt = QUERY_ANSWER_EVALUATION_PROMPT.replace(
        "<qa_pair>", json.dumps(qa_pairs, indent=2)
    )
    return prompt


def load_code_interpretation_results(
    file_path: str,
) -> list[CodeInterpreationTaskResult]:
    results = []
    with open(file_path, "r") as f:
        data = json.load(f)
        for item in data:
            if item.get("task", "") != "code_interpretation":
                continue

            documents = [
                Document(
                    source_name=doc["source_name"],
                    embedding_ids=doc["embedding_ids"],
                    similarity_score=doc["similarity_score"],
                )
                for doc in item.get("documents", [])
            ]

            pr_data = item["input"]

            results.append(
                CodeInterpreationTaskResult(
                    group=item["group"],
                    pr_id=pr_data["id"],
                    pr_title=pr_data["title"],
                    pr_description=pr_data["description"],
                    pr_hunks=[str(hunk) for hunk in pr_data["hunks"]],
                    documents=documents,
                    response=item["response"],
                )
            )
    return results


def generate_code_interpretation_evaluation_prompt(
    ci_results: list[CodeInterpreationTaskResult],
) -> str:
    # categorize descriptions by pull request
    pr_map = {}
    for id, result in enumerate(ci_results):
        pr_id = result.pr_id
        if pr_id not in pr_map:
            pr_map[pr_id] = {
                "pull_request": {
                    "id": result.pr_id,
                    "title": result.pr_title,
                    "description": result.pr_description,
                    "hunks": result.pr_hunks,
                },
                "descriptions": [],
            }
        pr_map[pr_id]["descriptions"].append(
            {"pair_id": id, "description": result.response}
        )

    # generate prompts for each pull request
    prompts = []
    for pr_id, pr_data in pr_map.items():
        pull_request_str = json.dumps(pr_data["pull_request"], indent=2)
        generated_descriptions_str = json.dumps(pr_data["descriptions"], indent=2)

        prompt = CODE_INTERPRETATION_EVALUATION_PROMPT.replace(
            "<pull_request>", pull_request_str
        ).replace("<generated_descriptions>", generated_descriptions_str)

        prompts.append(prompt)

    return "\n\n---\n\n".join(prompts)


def analyze_qa_retrieval_distribution(results: list[QATaskResult]):
    """
    For each different input query, analyze the difference in retrieved documents across different groups.
    """
    # Group results by query
    query_groups = {}
    for result in results:
        if result.query not in query_groups:
            query_groups[result.query] = []
        query_groups[result.query].append(result)

    print(f"\n=== Retrieval Distribution Analysis ===\n")
    print(f"Total unique queries: {len(query_groups)}\n")

    for query, group_results in query_groups.items():
        print(f"\nQuery: {query}")
        print(f"Number of groups: {len(group_results)}")
        print("-" * 80)

        # Show documents retrieved by each group
        for result in group_results:
            print(f"\nGroup: {result.group}")
            print(f"Documents retrieved: {len(result.documents)}")
            for i, doc in enumerate(result.documents, 1):
                print(
                    f"  {i}. {doc.source_name} (similarity: {doc.similarity_score:.4f})"
                )

        if len(group_results) > 1:
            print(f"\n--- Jaccard Similarity Between Groups ---")

            # Define specific comparisons we want to make
            target_comparisons = [
                ("adaptive_full", "adaptive_plain"),
                ("adaptive_full", "simple_full"),
            ]

            # Create a mapping from group name to result
            group_map = {result.group: result for result in group_results}

            for group1_name, group2_name in target_comparisons:
                if group1_name in group_map and group2_name in group_map:
                    result1 = group_map[group1_name]
                    result2 = group_map[group2_name]

                    sources1 = set(doc.source_name for doc in result1.documents)
                    sources2 = set(doc.source_name for doc in result2.documents)
                    intersection = len(sources1 & sources2)
                    union = len(sources1 | sources2)
                    jaccard = intersection / union if union > 0 else 0

                    print(
                        f"  {group1_name} vs {group2_name}: {jaccard:.4f} ({intersection}/{union})"
                    )

                    # Show which documents differ
                    only_in_group1 = sources1 - sources2
                    only_in_group2 = sources2 - sources1

                    if only_in_group1:
                        print(
                            f"    Only in {group1_name}: {len(only_in_group1)} document(s)"
                        )
                        for doc in list(only_in_group1):
                            print(f"      - {doc}")

                    if only_in_group2:
                        print(
                            f"    Only in {group2_name}: {len(only_in_group2)} document(s)"
                        )
                        for doc in list(only_in_group2):
                            print(f"      - {doc}")
                else:
                    if group1_name not in group_map:
                        print(
                            f"  {group1_name} vs {group2_name}: Group '{group1_name}' not found"
                        )
                    elif group2_name not in group_map:
                        print(
                            f"  {group1_name} vs {group2_name}: Group '{group2_name}' not found"
                        )

        print("\n" + "=" * 80)


def analyze_ci_retrieval_distribution(results: list[CodeInterpreationTaskResult]):
    """
    For each different pull request, analyze the difference in retrieved documents across different groups.
    """
    # Group results by pull request ID
    pr_groups = {}
    for result in results:
        if result.pr_id not in pr_groups:
            pr_groups[result.pr_id] = []
        pr_groups[result.pr_id].append(result)

    print(f"\n=== Retrieval Distribution Analysis for Code Interpretation ===\n")
    print(f"Total unique pull requests: {len(pr_groups)}\n")

    for pr_id, group_results in pr_groups.items():
        print(f"\nPull Request ID: {pr_id}")
        print(f"Number of groups: {len(group_results)}")
        print("-" * 80)

        # Show documents retrieved by each group
        for result in group_results:
            print(f"\nGroup: {result.group}")
            print(f"Documents retrieved: {len(result.documents)}")
            for i, doc in enumerate(result.documents, 1):
                print(
                    f"  {i}. {doc.source_name} (similarity: {doc.similarity_score:.4f})"
                )

        print("\n" + "=" * 80)

        if len(group_results) > 1:
            print(f"\n--- Jaccard Similarity Between Groups ---")

            # Define specific comparisons we want to make
            target_comparisons = [
                ("adaptive_full", "adaptive_plain"),
                ("adaptive_full", "simple_full"),
            ]

            # Create a mapping from group name to result
            group_map = {result.group: result for result in group_results}

            for group1_name, group2_name in target_comparisons:
                if group1_name in group_map and group2_name in group_map:
                    result1 = group_map[group1_name]
                    result2 = group_map[group2_name]

                    sources1 = set(doc.source_name for doc in result1.documents)
                    sources2 = set(doc.source_name for doc in result2.documents)
                    intersection = len(sources1 & sources2)
                    union = len(sources1 | sources2)
                    jaccard = intersection / union if union > 0 else 0

                    print(
                        f"  {group1_name} vs {group2_name}: {jaccard:.4f} ({intersection}/{union})"
                    )

                    # Show which documents differ
                    only_in_group1 = sources1 - sources2
                    only_in_group2 = sources2 - sources1

                    if only_in_group1:
                        print(
                            f"    Only in {group1_name}: {len(only_in_group1)} document(s)"
                        )
                        for doc in list(only_in_group1):
                            print(f"      - {doc}")

                    if only_in_group2:
                        print(
                            f"    Only in {group2_name}: {len(only_in_group2)} document(s)"
                        )
                        for doc in list(only_in_group2):
                            print(f"      - {doc}")
                else:
                    if group1_name not in group_map:
                        print(
                            f"  {group1_name} vs {group2_name}: Group '{group1_name}' not found"
                        )
                    elif group2_name not in group_map:
                        print(
                            f"  {group1_name} vs {group2_name}: Group '{group2_name}' not found"
                        )


if __name__ == "__main__":
    load_dotenv("../.env")

    results = load_code_interpretation_results("./data/evaluation_results_gemini.json")
    # prompt = generate_code_interpretation_evaluation_prompt(results)
    # print(prompt)
    analyze_ci_retrieval_distribution(results)
