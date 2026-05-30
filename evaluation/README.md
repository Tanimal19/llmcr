# Evaluation framework for LLMCR

Example code review:
```md
# Code Review Report
## Motivation

## Good points

## Bad points

## Suggestion

## Implementation details
### Filepath 1
- important detail 1
- important detail 2

## Issues
| Type | Title | Location | Detail |
| ---- | ----- | -------- | ------ |

# Appendix: Original Interpretation Results
### Change Description

### Change Motivation

# Appendix: Detailed Checklist Item Answers
### Checklist Item 1
Final Answer: ...
Analysis: ...
- Filepath:::Lines:::Reason
- Filepath:::Lines:::Reason

### Checklist Item 2
```

Example pull request:
```
Title:
Description:
Diff:
Comments:
```

# Datasets

Ground truth pull requests are collected from [Spring AI Github Repo](https://github.com/spring-projects/spring-ai/pulls) using the following criteria:
- Select all PRs created between 2.0.0-M1 released and 2026-05-15
- Filter out PRs with short description (less than 30 words), and less than 3 comments
- Select the most recent N=10 PRs


# Groups
We have three testing groups:
- A: Our framework (LLMs + SLMs)
- B: Our framework (LLMs)
- C: Single LLM



# Metrics
## Truth Grounding

1. Extract all mentioned entities (files, lines, modules, classes, functions, etc.) from the review
2. Extract all real entities from the diff

```
grounding_score = # of mentioned & real entities / # of mentioned entities
```
```
coverage_score = # of mentioned & real entities / # of total entities
```

## Review Alignment

Using BERTScore
```
Precision, Recall, F1 = BERTScore(Reference, Candidates)
```

### Comment
- *Reference*: sentences from human reviewer comments (paraphrase, split, merge using LLM)
- *Candidates*: sentences from "good points", "bad points", "suggestions", "issues"

### Interpretation
- *Reference*: sentences from PR description (paraphrase, split, merge using LLM)
- *Candidates*: sentences from "Change Description", "Change Motivation"


## Issue Correctness

For all issues (AI-mentioned and Human-mentioned), using LLM-as-Judge to check it's validaty, return True/False for each issue.
```
issue_correctness = # of valid issues / # of all issues
```


## Quality Score

Using LLM-as-a-Judge, prompt modify from the [CRScore](https://arxiv.org/abs/2506.00296) paper.
```
Comprehensiveness, Conciseness, Relevance = LLMJudge(Review, Code Change, Paraphrased Comments, Paraphrased Description)
```

## Repetitive Rate

1. Split the reivew into sentences
2. Clustering by similarity score

```
repetitive_rate = 1 - (# of clusters / # of sentences)
```
