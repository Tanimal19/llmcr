package com.example.llmcr;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.llmcr.service.faiss.FaissVectorStoreFactory;
import com.example.llmcr.service.rag.RAGService;
import com.example.llmcr.service.rag.augmentation.AnswerQueryTemplate;
import com.example.llmcr.service.rag.augmentation.CodeInterpretationTemplate;
import com.example.llmcr.service.rag.augmentation.CodeReviewTemplate;
import com.example.llmcr.service.rag.augmentation.RAGTemplate;
import com.example.llmcr.service.rag.augmentation.BasePullRequestTemplate.PullRequest;
import com.example.llmcr.service.rag.retrieval.AdaptiveKStrategy;
import com.example.llmcr.service.rag.retrieval.RetrievalStrategy;
import com.example.llmcr.service.rag.retrieval.SimpleRetrievalStrategy;
import com.example.llmcr.service.rag.retrieval.fusion.FusionStrategy;
import com.example.llmcr.service.rag.retrieval.fusion.RankFusionStrategy;
import com.example.llmcr.utils.JsonBackupUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name = "app.mode", havingValue = "evaluation")
public class EvaluationRunner implements CommandLineRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(EvaluationRunner.class);

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private FaissVectorStoreFactory FaissVectorStoreFactory;

    @Value("${evaluation.input.pullrequest.path}")
    private String inputPullRequestsPath;

    @Value("${evaluation.output.path:results.json}")
    private String outputPath;

    private List<PullRequest> pullRequests;

    private List<String> getEvaluationQueries() {
        return List.of(
                "What is the purpose of document joiner?",
                "Give me an simple example of building RAG service in Spring AI.",
                "What classes should I use to search documents based on similarity to a query?",
                "How builder pattern is used in Spring AI?");
    }

    private class TaskType {
        public final String name;
        private final Supplier<RAGTemplate> templateSupplier;
        public final List<?> inputs;

        public TaskType(String name, Supplier<RAGTemplate> templateSupplier, List<?> inputs) {
            this.name = name;
            this.templateSupplier = templateSupplier;
            this.inputs = inputs;
        }

        public RAGTemplate createTemplate() {
            return templateSupplier.get();
        }
    }

    private class GroupType {
        public final String name;
        private final RAGService ragService;

        public GroupType(String name, Supplier<RetrievalStrategy> retrievalSupplier,
                Supplier<FusionStrategy> fusionSupplier, int topK, String indexName) {
            this.name = name;

            this.ragService = new RAGService(chatModel, FaissVectorStoreFactory.create(indexName));
            this.ragService.setStrategy(retrievalSupplier.get(), fusionSupplier.get());
            this.ragService.setTopK(topK);
        }

        public RAGService getRagService() {
            return ragService;
        }
    }

    private List<TaskType> tasks;
    private List<GroupType> groups;

    @Override
    public void run(String... args) {
        LOGGER.info("Start evaluation.");
        this.pullRequests = readPullRequest(inputPullRequestsPath);

        this.tasks = List.of(
                new TaskType("code_interpretation",
                        CodeInterpretationTemplate::new, this.pullRequests),
                new TaskType("code_review", CodeReviewTemplate::new, this.pullRequests),
                new TaskType("query_answer", AnswerQueryTemplate::new, getEvaluationQueries()));

        this.groups = List.of(
                new GroupType("adaptive_full", AdaptiveKStrategy::new, RankFusionStrategy::new, 5, "full"),
                new GroupType("simple_full", SimpleRetrievalStrategy::new, RankFusionStrategy::new, 5, "full"),
                new GroupType("adaptive_plain", AdaptiveKStrategy::new, RankFusionStrategy::new, 5, "plain"));

        for (TaskType task : tasks) {
            runTask(task);
        }
    }

    private void runTask(TaskType task) {
        LOGGER.info("Starting task: " + task.name);

        int count = 0;
        for (Object input : task.inputs) {
            count++;
            LOGGER.info("Running input #" + count);
            LOGGER.info("input: " + input.toString().substring(0,
                    Math.min(100, input.toString().length())));

            for (GroupType group : groups) {
                LOGGER.info("Using group: " + group.name);
                long startTime = System.currentTimeMillis();

                RAGService ragService = group.getRagService();
                ragService.setRAGTemplate(task.createTemplate());

                Map<String, Object> response = ragService.generation(input);

                long duration = System.currentTimeMillis() - startTime;
                LOGGER.info("response: " + response.get("response"));
                saveResult(task.name, input, group.name, response, duration);
            }
        }

    }

    private void saveResult(String taskName, Object input, String groupName,
            Map<String, Object> response, long duration) {

        Map<String, Object> resultMap = new java.util.HashMap<>();
        resultMap.put("task", taskName);
        resultMap.put("input", input);
        resultMap.put("group", groupName);
        resultMap.put("response", response.get("response"));
        resultMap.put("documents", response.get("documents"));
        resultMap.put("duration", duration);

        try {
            JsonBackupUtils.appendJsonBackup(outputPath, resultMap);
        } catch (Exception e) {
            LOGGER.error("Failed to save result: {}", e.getMessage(), e);
        }
    }

    private static List<PullRequest> readPullRequest(String filePath) {
        try {
            ObjectMapper objectMapper = new ObjectMapper()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            return objectMapper.readValue(
                    new File(filePath),
                    new TypeReference<List<PullRequest>>() {
                    });
        } catch (Exception e) {
            throw new RuntimeException("Failed to read pull requests from: " + filePath, e);
        }
    }
}
