package com.llmcr.service.review;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.llmcr.agent.ComputationAgent;
import com.llmcr.agent.InterpretationAgent;
import com.llmcr.agent.PlanningAgent;
import com.llmcr.agent.SummaryAgent;
import com.llmcr.agent.ComputationAgent.ComputationAgentInput;
import com.llmcr.agent.ComputationAgent.ComputationAgentOutput;
import com.llmcr.agent.ComputationAgent.EvidenceItem;
import com.llmcr.agent.InterpretationAgent.InterpretationAgentInput;
import com.llmcr.agent.InterpretationAgent.InterpretationAgentOutput;
import com.llmcr.agent.PlanningAgent.PlanningAgentInput;
import com.llmcr.agent.PlanningAgent.PlanningAgentOutput;
import com.llmcr.agent.SummaryAgent.Issue;
import com.llmcr.agent.SummaryAgent.ItemAnswer;
import com.llmcr.agent.SummaryAgent.SummaryAgentInput;
import com.llmcr.agent.SummaryAgent.SummaryAgentOutput;
import com.llmcr.config.ApplicationProperties;
import com.llmcr.service.review.PullRequestParser.PullRequestData;

@Service
public class CodeReviewService {

    private static final Logger logger = LoggerFactory.getLogger(CodeReviewService.class);
    private static final DateTimeFormatter REPORT_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final InterpretationAgent interpretationAgent;
    private final PlanningAgent planningAgent;
    private final ComputationAgent computationAgent;
    private final SummaryAgent summaryAgent;

    private String outputDir;

    public CodeReviewService(
            ApplicationProperties applicationProperties,
            InterpretationAgent interpretationAgent,
            PlanningAgent planningAgent,
            ComputationAgent computationAgent,
            SummaryAgent summaryAgent) {
        this.outputDir = applicationProperties.getLogging().getReviewOutputDir();
        this.interpretationAgent = interpretationAgent;
        this.planningAgent = planningAgent;
        this.computationAgent = computationAgent;
        this.summaryAgent = summaryAgent;
    }

    public record CodeChange(String filePath, String diffContent) {
    }

    public record CodeReviewReport(
            SummaryAgentOutput mainReport,
            InterpretationAgentOutput interpretation,
            List<ItemAnswer> itemAnswers) {
    }

    /**
     * Run a full code review for the given git diff file and persist the report.
     */
    public Path review(String jsonFilePath, boolean useMockData) {

        PullRequestData prData = PullRequestParser.parseJsonFile(jsonFilePath);
        logger.info("review start for PR: {} (id={})", prData.title(), prData.prId());

        List<CodeChange> codeChanges = prData.changedFiles().stream()
                .map(file -> new CodeChange(file.path(), file.patch()))
                .toList();

        try {
            // TODO: integrate static analysis tool and populate codeAnalysis
            String codeAnalysis = null;

            InterpretationAgentOutput interpretation;
            PlanningAgentOutput planning;
            if (!useMockData) {
                logger.info("[INTERPRETATION] start");
                interpretation = interpretationAgent.execute(
                        new InterpretationAgentInput(codeChanges));

                logger.info("[PLANNING] start");
                planning = planningAgent.execute(
                        new PlanningAgentInput(codeChanges, interpretation, codeAnalysis));
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
                itemAnswers.add(new ItemAnswer(item, answer));
            }

            logger.info("[SUMMARY] start");
            SummaryAgentOutput reviewResult = summaryAgent.execute(
                    new SummaryAgentInput(codeChanges, codeAnalysis, itemAnswers));

            Path reportPath = writeReport(prData,
                    new CodeReviewReport(reviewResult, interpretation, itemAnswers));

            logger.info("code review for {} completed. Report written to: {}",
                    prData,
                    reportPath.toAbsolutePath());

            return reportPath;
        } catch (RuntimeException e) {
            throw e;
        }
    }

    private Path writeReport(PullRequestData prData, CodeReviewReport report) {
        try {
            Path dir = Paths.get(outputDir);
            Files.createDirectories(dir);

            String timestamp = REPORT_TIMESTAMP_FORMAT.format(Instant.now().atZone(ZoneId.systemDefault()));
            String fileName = "PR" + prData.prId() + "_" + timestamp + ".md";
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
        sb.append("\n\n");
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
        sb.append("\n\n");
        sb.append("# Appendix: Detailed Checklist Item Answers\n\n");
        if (report != null && report.itemAnswers() != null && !report.itemAnswers().isEmpty()) {
            for (ItemAnswer itemAnswer : report.itemAnswers()) {
                sb.append("### ").append(itemAnswer.checklistItemTitle()).append("\n\n");
                sb.append(itemAnswer.answer().finalAnswer()).append("\n");
                sb.append(itemAnswer.answer().analysis()).append("\n");
                for (EvidenceItem evdience : itemAnswer.answer().evidence()) {
                    sb.append("- ").append(evdience.file()).append(":::").append(evdience.lines()).append(":::")
                            .append(evdience.reason()).append("\n");
                }
            }
        } else {
            sb.append("_No checklist item answers available._\n\n");
        }

        return sb.toString();
    }
}
