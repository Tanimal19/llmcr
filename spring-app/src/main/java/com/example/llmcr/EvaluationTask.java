package com.example.llmcr;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.template.st.StTemplateRenderer;

import com.example.llmcr.service.RAGService;
import com.example.llmcr.service.rag.strategy.AdaptiveKStrategy;
import com.example.llmcr.service.rag.strategy.BaseRAGStrategy;
import com.example.llmcr.utils.JsonBackupUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public class EvaluationTask {
    private final String CODE_DESCRIPTION_PROMPT = """
            You are a code change describer. Given the code change hunks and relevant project context, your task is to generate a concise description on WHAT does the code change do and WHY does the code change is proposed.

            code change hunks:
            -----------------
            <hunks>
            -----------------

            relevant context:
            -----------------
            <context>
            -----------------

            Rules:
            1. Focus on the changes made in the code, not on unchanged parts.
            2. If you can't determine the purpose of the change from the given information, just say so, don't make assumptions.
            3. Do not use statements like "Based on the code change, it seems that...".
                        """;

    private final String CODE_REVIEW_PROMPT = """
            You are a code reviewer. Given the pull request description, code change hunks, and relevant project context, your task is to generate a concise review on the quality of the code change.

            pull request description:
            -----------------
            <description>
            -----------------

            code change hunks:
            -----------------
            <hunks>
            -----------------

            relevant context:
            -----------------
            <context>
            -----------------

            Rules:
            1. Focus on the changes made in the code, not on unchanged parts.
            2. If you can't determine the quality of the change from the given information, just say so, don't make assumptions.
            3. Do not use statements like "Based on the code change, it seems that...".
            4. You should end with a clear recommendation: "Approve", "Request changes", or "Reject".
            """;

    private record Hunk(String filepath, String content) {
    }

    private record PullRequest(String title, String description, List<Hunk> hunks) {
    }

    private final List<PullRequest> pullRequests = readPullRequest("../evaluation/pull_requests.json");
    private final String historyFile = "evaluation_history.json";

    public void run(RAGService ragService) {

        ragService.setStrategy(new AdaptiveKStrategy());
        runCodeDescriptionEvaluation(ragService, "adaptive_k");
        runCodeReviewEvaluation(ragService, "adaptive_k");

        ragService.setStrategy(new BaseRAGStrategy());
        runCodeDescriptionEvaluation(ragService, "base_rag");
        runCodeReviewEvaluation(ragService, "base_rag");

    }

    private void runCodeDescriptionEvaluation(RAGService ragService, String groupName) {
        List<Map<String, Object>> results = new java.util.ArrayList<>();
        for (PullRequest pr : pullRequests) {
            StringBuilder hunkBuilder = new StringBuilder();
            for (int i = 0; i < pr.hunks().size(); i++) {
                String hunkContent = pr.hunks().get(i).filepath() + "\n" + pr.hunks().get(i).content();
                hunkBuilder.append((i + 1)).append(". ").append(hunkContent).append("\n");
            }

            Map<String, Object> variables = Map.of(
                    "hunks", hunkBuilder.toString());
            results.add(ragService.generation(hunkBuilder.toString(),
                    createPromptTemplate(CODE_DESCRIPTION_PROMPT),
                    variables));
        }
        try {
            JsonBackupUtils.appendJsonBackup(historyFile,
                    Map.of("task", "code_description",
                            "group", groupName,
                            "results", results));
        } catch (Exception e) {
            throw new RuntimeException("Failed to write history: " + historyFile, e);
        }
    }

    private void runCodeReviewEvaluation(RAGService ragService, String groupName) {
        List<Map<String, Object>> results = new java.util.ArrayList<>();
        for (PullRequest pr : pullRequests) {
            StringBuilder hunkBuilder = new StringBuilder();
            for (int i = 0; i < pr.hunks().size(); i++) {
                String hunkContent = pr.hunks().get(i).filepath() + "\n" + pr.hunks().get(i).content();
                hunkBuilder.append((i + 1)).append(". ").append(hunkContent).append("\n");
            }

            Map<String, Object> variables = Map.of(
                    "description", pr.description(),
                    "hunks", hunkBuilder.toString());
            results.add(ragService.generation(pr.description(),
                    createPromptTemplate(CODE_REVIEW_PROMPT),
                    variables));
        }
        try {
            JsonBackupUtils.appendJsonBackup(historyFile,
                    Map.of("task", "code_review",
                            "group", groupName,
                            "results", results));
        } catch (Exception e) {
            throw new RuntimeException("Failed to write history: " + historyFile, e);
        }
    }

    private List<PullRequest> readPullRequest(String filePath) {
        try {
            ObjectMapper objectMapper = new ObjectMapper().configure(
                    DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                    false);
            return objectMapper.readValue(
                    new File(filePath),
                    new TypeReference<List<PullRequest>>() {
                    });
        } catch (Exception e) {
            throw new RuntimeException("Failed to read file: " + filePath, e);
        }
    }

    private PromptTemplate createPromptTemplate(String templateString) {
        return PromptTemplate.builder()
                .renderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
                .template(templateString)
                .build();
    }
}
