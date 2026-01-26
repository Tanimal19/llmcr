from dataclasses import dataclass
from google import genai
from dotenv import load_dotenv
import json
import os

QUERY_ANSWER_EVALUATION_PROMPT = """You are an expert software engineer with extensive experience in Spring AI project (https://github.com/spring-projects/spring-ai). Your task is to evaluate the quality of answers provided to questions about the project. A good answer should be relevant, accurate, and helpful in addressing the question asked. A bad answer provide unecessary, incorrect, or misleading information. 

<qa_pair>

Now look at the given question-answer pairs and give quality scores for each pair on a scale of 1 to 5. You should output with this format:
{pair_id}:{score}
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


def analyze_retrieval_distribution(results: list[QATaskResult]):
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


if __name__ == "__main__":
    load_dotenv("../.env")

    qa_results = load_qa_results("./data/evaluation_results_gemini.json")
    # prompt = generate_qa_evaluation_prompt(qa_results)
    # print(prompt)
    analyze_retrieval_distribution(qa_results)
