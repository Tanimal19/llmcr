package com.example.llmcr;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.llmcr.faiss.FaissVectorStore;
import com.example.llmcr.faiss.FaissVectorStoreFactory;
import com.example.llmcr.service.rag.RAGService;
import com.example.llmcr.service.rag.augmentation.CodeInterpretationTemplate;
import com.example.llmcr.service.rag.augmentation.CodeReviewTemplate;
import com.example.llmcr.service.rag.augmentation.RAGTemplate;
import com.example.llmcr.service.rag.augmentation.BasePullRequestTemplate.PullRequest;
import com.example.llmcr.service.rag.retrieval.AdaptiveKStrategy;
import com.example.llmcr.service.rag.retrieval.RetrievalStrategy;
import com.example.llmcr.service.rag.retrieval.SimpleRetrievalStrategy;
import com.example.llmcr.service.rag.retrieval.fusion.RankFusionStrategy;
import com.example.llmcr.utils.JsonBackupUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;

@Component
@ConditionalOnProperty(name = "app.mode", havingValue = "evaluation")
public class EvaluationRunner implements CommandLineRunner {
    @Autowired
    private ChatModel chatModel;

    @Autowired
    private FaissVectorStoreFactory FaissVectorStoreFactory;

    @Value("${evaluation.input.path}")
    private String inputFilePath;

    private List<PullRequest> pullRequests;
    private String historyFile = "evaluation_history.json";

    private RateLimiter rateLimiter = RateLimiter.create(1 / 60.0);

    @Override
    public void run(String... args) {
        this.pullRequests = readPullRequest(inputFilePath);

        runGroup("adaptive-enriched");
        // runGroup("simple-enriched");
        // runGroup("adaptive-plain");
    }

    private void runGroup(String group) {
        RAGService ragService;

        if (group.equalsIgnoreCase("adaptive-enriched")) {
            ragService = new RAGService(chatModel, FaissVectorStoreFactory.create("enriched"));
            ragService.setStrategy(new AdaptiveKStrategy(), new RankFusionStrategy());
            ragService.setTopK(15);

        } else if (group.equalsIgnoreCase("simple-enriched")) {
            ragService = new RAGService(chatModel, FaissVectorStoreFactory.create("enriched"));
            ragService.setStrategy(new SimpleRetrievalStrategy(), new RankFusionStrategy());
            ragService.setTopK(15);

        } else if (group.equalsIgnoreCase("adaptive-plain")) {
            ragService = new RAGService(chatModel, FaissVectorStoreFactory.create("plain"));
            ragService.setStrategy(new AdaptiveKStrategy(), new RankFusionStrategy());
            ragService.setTopK(15);

        } else {
            throw new IllegalArgumentException("Unknown evaluation group: " + group);
        }

        System.out.println("+ Running evaluation for group: " + group);
        runTask(ragService, new CodeInterpretationTemplate(), group, "code_interpretation");
        // runTask(ragService, new CodeReviewTemplate(), group, "code_review");
    }

    private void runTask(RAGService ragService, RAGTemplate ragTemplate, String group, String taskName) {
        System.out.println("+ Starting task: " + taskName);
        ragService.setRAGTemplate(ragTemplate);

        for (PullRequest pr : pullRequests) {
            Map<String, Object> response = new HashMap<>(ragService.generation(pr));
            response.put("group", group);
            response.put("task", taskName);
            response.put("pr_id", pr.id());
            try {
                JsonBackupUtils.appendJsonBackup(historyFile, response);
                System.out.println("+ Saved evaluation result for PR #" + pr.id());
            } catch (Exception e) {
                e.printStackTrace();
            }

            rateLimiter.acquire();
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
