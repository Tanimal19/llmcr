package com.llmcr.service.review;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.llmcr.service.review.agent.SummaryAgent.Issue;
import com.llmcr.service.review.agent.InterpretationAgent.InterpretationAgentOutput;
import com.llmcr.service.review.agent.SummaryAgent.SummaryAgentOutput;
import com.llmcr.service.review.trace.ReviewTraceCollector;
import com.llmcr.service.review.trace.ReviewTraceContext;
import com.llmcr.service.review.workflow.ChainWorkflow;
import com.llmcr.service.review.workflow.ChainWorkflow.ReviewResult;
import com.llmcr.util.GitDiffParser;
import com.llmcr.util.GitDiffParser.CodeChange;

@Service
public class CodeReviewService {

    private static final Logger log = LoggerFactory.getLogger(CodeReviewService.class);

    private final ChainWorkflow chainWorkflow;
    private final ObjectMapper objectMapper;

    @Value("${llmcr.review.output-dir}")
    private String outputDir;

    public CodeReviewService(ChainWorkflow chainWorkflow) {
        this.chainWorkflow = chainWorkflow;
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Run a full code review for the given git diff file and persist the report.
     *
     * @param diffFilePath absolute or relative path to the {@code .diff} file.
     * @return path of the written JSON report file.
     */
    public Path review(String diffFilePath) {
        ReviewTraceCollector trace = new ReviewTraceCollector();
        trace.putMetadata("diffFilePath", diffFilePath);
        ReviewTraceContext.start(trace);

        try {
            log.info("parsing diff file={}", diffFilePath);
            List<CodeChange> codeChanges = GitDiffParser.parseDiffFile(diffFilePath);

            // TODO: integrate static analysis tool and populate codeAnalysis
            String codeAnalysis = null;

            log.info("starting chain workflow");
            ReviewResult reviewResult = chainWorkflow.run(codeChanges, codeAnalysis);

            Path reportPath = writeReport(diffFilePath, reviewResult.interpretation(), reviewResult.summary());

            trace.complete(null);
            return reportPath;
        } catch (RuntimeException e) {
            trace.complete(e);
            throw e;
        } finally {
            try {
                Path tracePath = writeTrace(diffFilePath, trace);
                log.info("review trace written to {}", tracePath.toAbsolutePath());
            } finally {
                ReviewTraceContext.clear();
            }
        }
    }

    private Path writeReport(String diffFilePath, InterpretationAgentOutput interpretation, SummaryAgentOutput report) {
        try {
            Path dir = Paths.get(outputDir);
            Files.createDirectories(dir);

            String baseName = Paths.get(diffFilePath).getFileName().toString()
                    .replaceAll("\\.diff$", "");
            String fileName = baseName + "_" + Instant.now().toEpochMilli() + ".md";
            Path reportPath = dir.resolve(fileName);

            String markdown = buildMarkdownReport(interpretation, report);
            Files.writeString(reportPath, markdown);
            log.info("report written to {}", reportPath.toAbsolutePath());
            return reportPath;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write review report", e);
        }
    }

    private String buildMarkdownReport(InterpretationAgentOutput interpretation, SummaryAgentOutput report) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Code Review Report\n\n");

        // Code Interpretation section
        sb.append("## Code Interpretation\n\n");
        if (interpretation != null) {
            sb.append("### What Changed\n\n");
            sb.append(interpretation.changeDescription()).append("\n\n");
            sb.append("### Why These Changes\n\n");
            sb.append(interpretation.changeMotivation()).append("\n\n");
        } else {
            sb.append("_No interpretation available._\n\n");
        }

        // Summary Report section
        sb.append("## Summary Report\n\n");
        if (report != null) {
            sb.append("### Overall Verdict\n\n");
            sb.append(report.overallVerdict()).append("\n\n");

            sb.append("### Summary\n\n");
            sb.append(report.summary()).append("\n\n");

            if (report.goodPoints() != null && !report.goodPoints().isEmpty()) {
                sb.append("### Good Points\n\n");
                for (String point : report.goodPoints()) {
                    sb.append("- ").append(point).append("\n");
                }
                sb.append("\n");
            }

            if (report.badPoints() != null && !report.badPoints().isEmpty()) {
                sb.append("### Bad Points\n\n");
                for (String point : report.badPoints()) {
                    sb.append("- ").append(point).append("\n");
                }
                sb.append("\n");
            }

            if (report.issues() != null && !report.issues().isEmpty()) {
                sb.append("### Issues\n\n");
                sb.append("| Severity | Type | Title | Location | Detail |\n");
                sb.append("|----------|------|-------|----------|--------|\n");
                for (Issue issue : report.issues()) {
                    String location = issue.location() != null ? issue.location() : "";
                    String type = issue.type() != null ? issue.type() : "";
                    sb.append("| ").append(issue.severity())
                            .append(" | ").append(type)
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

    private Path writeTrace(String diffFilePath, ReviewTraceCollector trace) {
        try {
            Path dir = Paths.get(outputDir);
            Files.createDirectories(dir);

            String baseName = Paths.get(diffFilePath).getFileName().toString()
                    .replaceAll("\\.diff$", "");
            String fileName = baseName + "_" + Instant.now().toEpochMilli() + ".trace.json";
            Path tracePath = dir.resolve(fileName);

            objectMapper.writeValue(tracePath.toFile(), trace);
            return tracePath;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write review trace", e);
        }
    }
}
