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
import com.llmcr.agent.ComputationAgent.ComputationAgentOutput;
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

    public record CodeReviewReport(
            SummaryAgentOutput mainReport,
            InterpretationAgentOutput interpretation,
            List<ItemAnswer> itemAnswers) {
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
                logger.info("[INTERPRETATION] start");
                interpretation = interpretationAgent.execute(
                        new InterpretationAgentInput(codeChanges));
                try {
                    logger.info("[INTERPRETATION] result=\n{}", objectMapper.writeValueAsString(interpretation));
                } catch (Exception e) {
                    logger.info("[INTERPRETATION] result=\n{}", interpretation, e);
                }

                logger.info("[PLANNING] start");
                planning = planningAgent.execute(
                        new PlanningAgentInput(codeChanges, interpretation, codeAnalysis));
                try {
                    logger.info("[PLANNING] result=\n{}", objectMapper.writeValueAsString(planning));
                } catch (Exception e) {
                    logger.info("[PLANNING] result=\n{}", planning, e);
                }

            } else {
                interpretation = MockReviewData.MOCK_INTERPRETATION;
                planning = MockReviewData.MOCK_PLANNING;
                logger.info("using mock interpretation and planning results");
            }

            logger.info("[COMPUTATION] start", planning.checklistItems().size());
            List<ItemAnswer> itemAnswers = new ArrayList<>();
            for (String item : planning.checklistItems()) {
                logger.info("[COMPUTATION] item={}", item);
                ComputationAgentOutput answer = computationAgent.execute(new ComputationAgentInput(codeChanges, item));
                try {
                    logger.info("[COMPUTATION] item={} | output=\n{}", item, objectMapper.writeValueAsString(answer));
                } catch (Exception e) {
                    logger.info("[COMPUTATION] item={} | output=\n{}", item, answer, e);
                }

                String answerString = "Final Answer: " + answer.finalAnswer() + "\n"
                        + "Analysis: " + answer.analysis() + "\n"
                        + "Evidence:\n" + (answer.evidence() != null
                                ? answer.evidence().stream()
                                        .map(e -> String.format("- file: %s, lines: %s, reason: %s",
                                                e.file(), e.lines(), e.reason()))
                                        .reduce((a, b) -> a + "\n" + b)
                                        .orElse("No evidence provided.")
                                : "No evidence provided.");
                itemAnswers.add(new ItemAnswer(item, answerString));
            }

            logger.info("[SUMMARY] start");
            SummaryAgentOutput reviewResult = summaryAgent.execute(
                    new SummaryAgentInput(codeChanges, codeAnalysis, itemAnswers));
            try {
                logger.info("[SUMMARY] result=\n{}", objectMapper.writeValueAsString(reviewResult));
            } catch (Exception e) {
                logger.info("[SUMMARY] result=\n{}", reviewResult, e);
            }

            Path reportPath = writeReport(diffFilePath,
                    new CodeReviewReport(reviewResult, interpretation, itemAnswers));

            logger.info("code review for {} completed. Report written to: {}",
                    diffFilePath,
                    reportPath.toAbsolutePath());

            return reportPath;
        } catch (RuntimeException e) {
            throw e;
        }
    }

    private Path writeReport(String diffFilePath, CodeReviewReport report) {
        try {
            Path dir = Paths.get(outputDir);
            Files.createDirectories(dir);

            String baseName = Paths.get(diffFilePath).getFileName().toString()
                    .replaceAll("\\.diff$", "");
            String fileName = baseName + "_" + Instant.now().toEpochMilli() + ".md";
            Path reportPath = dir.resolve(fileName);

            String markdown = buildMarkdownReport(report);
            Files.writeString(reportPath, markdown);
            return reportPath;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write review report", e);
        }
    }

    private String buildMarkdownReport(CodeReviewReport report) {
        StringBuilder sb = new StringBuilder();

        // Summary Report section
        sb.append("# Code Review Report\n\n");
        if (report != null && report.mainReport() != null) {
            sb.append("## Motivation\n\n");
            if (report.mainReport().motivation() != null && !report.mainReport().motivation().isBlank()) {
                sb.append(report.mainReport().motivation()).append("\n\n");
            } else {
                sb.append("_No motivation provided._\n\n");
            }

            sb.append("## Good Points\n\n");
            if (report.mainReport().goodPoints() != null && !report.mainReport().goodPoints().isEmpty()) {
                for (String point : report.mainReport().goodPoints()) {
                    sb.append("- ").append(point).append("\n");
                }
                sb.append("\n");
            } else {
                sb.append("_No good points provided._\n\n");
            }

            sb.append("## Bad Points\n\n");
            if (report.mainReport().badPoints() != null && !report.mainReport().badPoints().isEmpty()) {
                for (String point : report.mainReport().badPoints()) {
                    sb.append("- ").append(point).append("\n");
                }
                sb.append("\n");
            } else {
                sb.append("_No bad points provided._\n\n");
            }

            sb.append("## Suggestion\n\n");
            if (report.mainReport().suggestion() != null && !report.mainReport().suggestion().isBlank()) {
                sb.append(report.mainReport().suggestion()).append("\n\n");
            } else {
                sb.append("_No suggestion provided._\n\n");
            }

            sb.append("## Implementation Details\n\n");
            if (report.mainReport().implementationDetails() != null
                    && !report.mainReport().implementationDetails().isEmpty()) {
                for (var detailsByFile : report.mainReport().implementationDetails()) {
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
            if (report.mainReport().issues() != null && !report.mainReport().issues().isEmpty()) {
                sb.append("| Type | Title | Location | Detail |\n");
                sb.append("|------|-------|----------|--------|\n");
                for (Issue issue : report.mainReport().issues()) {
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

        // Appendix with interpretation results
        sb.append("---\n\n");
        sb.append("# Appendix: Original Interpretation Results\n\n");
        if (report != null && report.interpretation() != null) {
            InterpretationAgentOutput interpretation = report.interpretation();
            if (interpretation.changeDescription() != null) {
                sb.append("### Change Description\n\n");
                sb.append(interpretation.changeDescription()).append("\n\n");
            }
            if (interpretation.changeMotivation() != null) {
                sb.append("### Change Motivation\n\n");
                sb.append(interpretation.changeMotivation()).append("\n\n");
            }
        } else {
            sb.append("_No interpretation results available._\n\n");
        }

        // Appendix with detailed checklist item answers
        sb.append("---\n\n");
        sb.append("# Appendix: Detailed Checklist Item Answers\n\n");
        if (report != null && report.itemAnswers() != null && !report.itemAnswers().isEmpty()) {
            for (ItemAnswer itemAnswer : report.itemAnswers()) {
                sb.append("### Checklist Item: ").append(itemAnswer.checklistItemTitle()).append("\n\n");
                sb.append(itemAnswer.answer()).append("\n\n");
            }
        } else {
            sb.append("_No checklist item answers available._\n\n");
        }

        return sb.toString();
    }
}
