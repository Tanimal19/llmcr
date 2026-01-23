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
