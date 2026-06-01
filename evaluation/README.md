# Evaluation framework for LLMCR

# Datasets
We select pull requests from Spring AI’s Github repository that satisfied:
- Created after v2.0.0-M1 release tag and before 2026-04-01
- Description word count >= 30
- Number of comments >= 5
- Changed files <= 20

As a result, 10 pull requests are select:
[#5659, #5585, #5506, #5483, #5440, #5416, #5414, #5292, #5252, #5091]

We generated reference sentences from PRs using Nemotron-3-4B (Q4_K_M).



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

Using BERTScore (Sentence-BERT)
```
Precision, Recall, F1 = BERTScore(Reference, Candidates)
```

### Comment
- *Reference*: sentences from human reviewer comments (paraphrase, split, merge using LLM)
- *Candidates*: sentences from "good points", "bad points", "suggestions", "issues"

### Interpretation
- *Reference*: sentences from PR description (paraphrase, split, merge using LLM)
- *Candidates*: sentences from "Change Description", "Change Motivation"


## Quality Score

Using LLM-as-a-Judge, prompt modify from the [CRScore](https://arxiv.org/abs/2506.00296) paper.
```
Comprehensiveness, Conciseness, Relevance (integer score 1-5) = LLMJudge(Review, Code Change, Paraphrased Comments, Paraphrased Description)
```

## Repetitive Rate

1. Split the reivew into sentences
2. Clustering by similarity score

```
repetitive_rate = 1 - (# of clusters / # of sentences)
```
