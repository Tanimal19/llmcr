CODE_DESCRIPTION_PROMPT = """
You are a code change describer. Given the code change hunks and relevant project context, your task is to generate a concise description on WHAT does the code change do and WHY does the code change is proposed.

code change hunks:
-----------------
<query>
-----------------

relevant context:
-----------------
<context>
-----------------

Rules:
1. Focus on the changes made in the code, not on unchanged parts.
2. If you can't determine the purpose of the change from the given information, just say so, don't make assumptions.
3. Do not use statements like "Based on the code change, it seems that...".
"""

CODE_REVIEW_PROMPT = """
You are a code reviewer. Given the pull request description, code change hunks, and relevant project context, your task is to generate a concise review on the quality of the code change.

<query>

"""


def build_test_cases():
