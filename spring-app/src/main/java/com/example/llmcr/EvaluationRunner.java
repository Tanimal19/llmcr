package com.example.llmcr;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.example.llmcr.faiss.FaissVectorStore;
import com.example.llmcr.faiss.FaissVectorStoreFactory;
import com.example.llmcr.service.rag.RAGService;
import com.example.llmcr.service.rag.augmentation.CodeInterpretationPromptBuilder;
import com.example.llmcr.service.rag.augmentation.CodeReviewPromptBuilder;
import com.example.llmcr.service.rag.augmentation.PromptBuilder;
import com.example.llmcr.service.rag.augmentation.BasePullRequestPromptBuilder.PullRequest;
import com.example.llmcr.service.rag.retrieval.AdaptiveKStrategy;
import com.example.llmcr.service.rag.retrieval.RetrievalStrategy;
import com.example.llmcr.service.rag.retrieval.SimpleRAGStrategy;
import com.example.llmcr.utils.JsonBackupUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class EvaluationRunner {
    private final ChatModel chatModel;
    private final FaissVectorStoreFactory FaissVectorStoreFactory;

    private final List<PullRequest> pullRequests;
    private final String historyFile = "evaluation_history.json";

    @Autowired
    public EvaluationRunner(ChatModel chatModel, FaissVectorStoreFactory FaissVectorStoreFactory) {
        this.chatModel = chatModel;
        this.FaissVectorStoreFactory = FaissVectorStoreFactory;
        this.pullRequests = readPullRequest("../evaluation/pull_requests.json");
    }

    public void runAllGroups() {
        runGroup("adaptive-enrich");
        runGroup("simple-enrich");
        runGroup("adaptive-plain");
    }

    public void runGroup(String group) {
        RetrievalStrategy retrievalStrategy;
        FaissVectorStore vectorStore;

        if (group.equalsIgnoreCase("adaptive-enrich")) {
            retrievalStrategy = new AdaptiveKStrategy();
            vectorStore = FaissVectorStoreFactory.create("enrich");
        } else if (group.equalsIgnoreCase("simple-enrich")) {
            retrievalStrategy = new SimpleRAGStrategy();
            vectorStore = FaissVectorStoreFactory.create("enrich");
        } else if (group.equalsIgnoreCase("adaptive-plain")) {
            retrievalStrategy = new AdaptiveKStrategy();
            vectorStore = FaissVectorStoreFactory.create("plain");
        } else {
            throw new IllegalArgumentException("Unknown evaluation group: " + group);
        }

        System.out.println("+ Running evaluation for group: " + group);
        RAGService ragService = new RAGService(chatModel, vectorStore);
        ragService.setStrategy(retrievalStrategy);

        runTask(ragService, new CodeInterpretationPromptBuilder(), group, "code_interpretation");
        runTask(ragService, new CodeReviewPromptBuilder(), group, "code_review");
    }

    private void runTask(RAGService ragService, PromptBuilder promptBuilder, String group, String taskName) {
        System.out.println("+ Starting task: " + taskName);
        ragService.setPromptBuilder(promptBuilder);

        for (PullRequest pr : pullRequests) {
            Map<String, Object> response = ragService.generation(pr);
            response.put("group", group);
            response.put("task", taskName);
            response.put("pr_id", pr.id());
            try {
                JsonBackupUtils.appendJsonBackup(historyFile, response);
                System.out.println("+ Saved evaluation result for PR #" + pr.id());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static List<PullRequest> readPullRequest(String filePath) {
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
}
