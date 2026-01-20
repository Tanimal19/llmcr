package com.example.llmcr;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.llmcr.faiss.FaissVectorStoreFactory;
import com.example.llmcr.service.rag.RAGService;
import com.example.llmcr.service.rag.augmentation.CodeInterpretationTemplate;
import com.example.llmcr.service.rag.augmentation.CodeReviewTemplate;
import com.example.llmcr.service.rag.augmentation.RAGTemplate;
import com.example.llmcr.service.rag.augmentation.BasePullRequestTemplate.PullRequest;
import com.example.llmcr.service.rag.retrieval.AdaptiveKStrategy;
import com.example.llmcr.service.rag.retrieval.SimpleRetrievalStrategy;
import com.example.llmcr.service.rag.retrieval.fusion.RankFusionStrategy;
import com.example.llmcr.utils.JsonBackupUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

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
    private String resultFile = "result.json";

    private static final Logger logger = java.util.logging.Logger.getLogger(EvaluationRunner.class.getName());

    @Override
    public void run(String... args) {
        this.pullRequests = readPullRequest(inputFilePath);

        runGroup("adaptive-enriched");
        runGroup("simple-enriched");
        runGroup("adaptive-plain");
    }

    private void runGroup(String group) {
        RAGService ragService;

        if (group.equalsIgnoreCase("adaptive-enriched")) {
            ragService = new RAGService(chatModel, FaissVectorStoreFactory.create("enriched"));
            ragService.setStrategy(new AdaptiveKStrategy(), new RankFusionStrategy());
            ragService.setTopK(20);

        } else if (group.equalsIgnoreCase("simple-enriched")) {
            ragService = new RAGService(chatModel, FaissVectorStoreFactory.create("enriched"));
            ragService.setStrategy(new SimpleRetrievalStrategy(), new RankFusionStrategy());
            ragService.setTopK(20);

        } else if (group.equalsIgnoreCase("adaptive-plain")) {
            ragService = new RAGService(chatModel, FaissVectorStoreFactory.create("plain"));
            ragService.setStrategy(new AdaptiveKStrategy(), new RankFusionStrategy());
            ragService.setTopK(20);

        } else {
            throw new IllegalArgumentException("Unknown evaluation group: " + group);
        }

        logger.info("Starting evaluation for group: " + group);
        runTask(ragService, new CodeInterpretationTemplate(), group, "interpretation");
        runTask(ragService, new CodeReviewTemplate(), group, "review");
    }

    private void runTask(RAGService ragService, RAGTemplate ragTemplate, String group, String taskName) {
        logger.info("Starting task: " + taskName);
        ragService.setRAGTemplate(ragTemplate);

        for (PullRequest pr : pullRequests) {
            logger.info("Start generation for PR #" + pr.id());
            Map<String, Object> response = new HashMap<>(ragService.generation(pr));
            response.put("group", group);
            response.put("task", taskName);
            response.put("pr_id", pr.id());

            logger.info("Response for PR #" + pr.id() + ": " + response.get("response"));

            try {
                JsonBackupUtils.appendJsonBackup(resultFile, response);
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
