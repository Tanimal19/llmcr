package com.llmcr.service.review;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.llmcr.agent.ComputationAgent;
import com.llmcr.agent.InterpretationAgent;
import com.llmcr.agent.PlanningAgent;
import com.llmcr.agent.SummaryAgent;
import com.llmcr.agent.ComputationAgent.ComputationAgentInput;
import com.llmcr.agent.InterpretationAgent.InterpretationAgentInput;
import com.llmcr.agent.InterpretationAgent.InterpretationAgentOutput;
import com.llmcr.agent.PlanningAgent.PlanningAgentInput;
import com.llmcr.agent.PlanningAgent.PlanningAgentOutput;
import com.llmcr.agent.SummaryAgent.Issue;
import com.llmcr.agent.SummaryAgent.ItemAnswer;
import com.llmcr.agent.SummaryAgent.SummaryAgentInput;
import com.llmcr.agent.SummaryAgent.SummaryAgentOutput;
import com.llmcr.util.GitDiffParser;
import com.llmcr.util.GitDiffParser.CodeChange;

@Service
public class CodeReviewService {

    private static final Logger logger = LoggerFactory.getLogger(CodeReviewService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final InterpretationAgent interpretationAgent;
    private final PlanningAgent planningAgent;
    private final ComputationAgent computationAgent;
    private final SummaryAgent summaryAgent;

    @Value("${llmcr.review.output-dir}")
    private String outputDir;

    public CodeReviewService(
            InterpretationAgent interpretationAgent,
            PlanningAgent planningAgent,
            ComputationAgent computationAgent,
            SummaryAgent summaryAgent) {
        this.interpretationAgent = interpretationAgent;
        this.planningAgent = planningAgent;
        this.computationAgent = computationAgent;
        this.summaryAgent = summaryAgent;
    }

    /**
     * Run a full code review for the given git diff file and persist the report.
     *
     * @param diffFilePath absolute or relative path to the {@code .diff} file.
     * @return path of the written JSON report file.
     */
    public Path review(String diffFilePath, boolean useMockData) {

        try {
            if (useMockData) {
                diffFilePath = MockReviewData.MOCK_DIFFPATH;
            }

            logger.info("parsing diff file={}", diffFilePath);
            List<CodeChange> codeChanges = GitDiffParser.parseDiffFile(diffFilePath);

            // TODO: integrate static analysis tool and populate codeAnalysis
            String codeAnalysis = null;

            InterpretationAgentOutput interpretation;
            PlanningAgentOutput planning;
            if (!useMockData) {
                logger.info("step=interpretation");
                interpretation = interpretationAgent.execute(
                        new InterpretationAgentInput(codeChanges));
                try {
                    logger.info("interpretation result:\n{}",
                            objectMapper.writeValueAsString(interpretation));
                } catch (Exception e) {
                    logger.info("interpretation result: {}", interpretation, e);
                }

                logger.info("step=planning");
                planning = planningAgent.execute(
                        new PlanningAgentInput(codeChanges, interpretation, codeAnalysis));
                try {
                    logger.info("planning result:\n{}", objectMapper.writeValueAsString(planning));
                } catch (Exception e) {
                    logger.info("planning result: {}", planning, e);
                }
            } else {
                interpretation = MockReviewData.MOCK_INTERPRETATION;
                planning = MockReviewData.MOCK_PLANNING;
                logger.info("using mock interpretation and planning results");
            }

            logger.info("step=computation items={}", planning.checklistItems().size());
            List<ItemAnswer> itemAnswers = new ArrayList<>();
            for (String item : planning.checklistItems()) {
                logger.debug("item={}", item);
                String answer = computationAgent.execute(new ComputationAgentInput(codeChanges, item));
                itemAnswers.add(new ItemAnswer(item, answer));
            }
            try {
                logger.info("computation results:\n{}", objectMapper.writeValueAsString(itemAnswers));
            } catch (Exception e) {
                logger.info("computation results: {}", itemAnswers, e);
            }

            logger.info("step=summary");
            SummaryAgentOutput reviewResult = summaryAgent.execute(
                    new SummaryAgentInput(codeChanges, codeAnalysis, itemAnswers));
            try {
                logger.info("summary result:\n{}", objectMapper.writeValueAsString(reviewResult));
            } catch (Exception e) {
                logger.info("summary result: {}", reviewResult, e);
            }

            Path reportPath = writeReport(diffFilePath, reviewResult);

            return reportPath;
        } catch (RuntimeException e) {
            throw e;
        }
    }

    private Path writeReport(String diffFilePath, SummaryAgentOutput reviewResult) {
        try {
            Path dir = Paths.get(outputDir);
            Files.createDirectories(dir);

            String baseName = Paths.get(diffFilePath).getFileName().toString()
                    .replaceAll("\\.diff$", "");
            String fileName = baseName + "_" + Instant.now().toEpochMilli() + ".md";
            Path reportPath = dir.resolve(fileName);

            String markdown = buildMarkdownReport(reviewResult);
            Files.writeString(reportPath, markdown);
            logger.info("report written to {}", reportPath.toAbsolutePath());
            return reportPath;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write review report", e);
        }
    }

    private String buildMarkdownReport(SummaryAgentOutput reviewResult) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Code Review Report\n\n");

        // Summary Report section
        if (reviewResult != null) {
            sb.append("## Motivation\n\n");
            if (reviewResult.motivation() != null && !reviewResult.motivation().isBlank()) {
                sb.append(reviewResult.motivation()).append("\n\n");
            } else {
                sb.append("_No motivation provided._\n\n");
            }

            sb.append("## Good Points\n\n");
            if (reviewResult.goodPoints() != null && !reviewResult.goodPoints().isEmpty()) {
                for (String point : reviewResult.goodPoints()) {
                    sb.append("- ").append(point).append("\n");
                }
                sb.append("\n");
            } else {
                sb.append("_No good points provided._\n\n");
            }

            sb.append("## Bad Points\n\n");
            if (reviewResult.badPoints() != null && !reviewResult.badPoints().isEmpty()) {
                for (String point : reviewResult.badPoints()) {
                    sb.append("- ").append(point).append("\n");
                }
                sb.append("\n");
            } else {
                sb.append("_No bad points provided._\n\n");
            }

            sb.append("## Suggestion\n\n");
            if (reviewResult.suggestion() != null && !reviewResult.suggestion().isBlank()) {
                sb.append(reviewResult.suggestion()).append("\n\n");
            } else {
                sb.append("_No suggestion provided._\n\n");
            }

            sb.append("## Implementation Details\n\n");
            if (reviewResult.implementationDetails() != null && !reviewResult.implementationDetails().isEmpty()) {
                for (var detailsByFile : reviewResult.implementationDetails()) {
                    String filename = detailsByFile.filename() != null ? detailsByFile.filename() : "(unknown file)";
                    sb.append("#### ").append(filename).append("\n\n");
                    if (detailsByFile.details() != null && !detailsByFile.details().isEmpty()) {
                        for (String detail : detailsByFile.details()) {
                            sb.append("- ").append(detail).append("\n");
                        }
                    } else {
                        sb.append("- _No details provided._\n");
                    }
                    sb.append("\n");
                }
            }

            sb.append("## Issues\n\n");
            if (reviewResult.issues() != null && !reviewResult.issues().isEmpty()) {
                sb.append("| Type | Title | Location | Detail |\n");
                sb.append("|------|-------|----------|--------|\n");
                for (Issue issue : reviewResult.issues()) {
                    String location = issue.location() != null ? issue.location() : "";
                    String type = issue.type() != null ? issue.type() : "";
                    sb.append("| ").append(type)
                            .append(" | ").append(issue.title())
                            .append(" | ").append(location)
                            .append(" | ").append(issue.detail())
                            .append(" |\n");
                }
                sb.append("\n");
            }
        } else {
            sb.append("_No summary available._\n\n");
        }

        return sb.toString();
    }
}
