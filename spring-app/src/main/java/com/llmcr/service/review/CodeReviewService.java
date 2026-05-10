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

    private static final Logger log = LoggerFactory.getLogger(CodeReviewService.class);
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
    public Path review(String diffFilePath) {

        try {
            log.info("parsing diff file={}", diffFilePath);
            List<CodeChange> codeChanges = GitDiffParser.parseDiffFile(diffFilePath);

            // TODO: integrate static analysis tool and populate codeAnalysis
            String codeAnalysis = null;

            InterpretationAgentOutput mockInterpretationResult = new InterpretationAgentOutput(
                    "Introduced a new integration test class `McpToolInputSchemaIT` that verifies the correct generation of JSON schemas for MCP tool inputs. This test class specifically focuses on ensuring that the `required` field in the generated schema accurately reflects the nullability of method parameters and nested record fields, respecting annotations like `@Nullable` and `@JsonProperty(required = false)`. It also includes an end-to-end test to confirm that a tool with nested record parameters is correctly registered and has the expected schema within the MCP server context.\n\nRefactored `SpringAiSchemaModule` into an abstract base class `AbstractSpringAiSchemaModule` and created concrete implementations `McpSpringAiSchemaModule` and `SpringAiSchemaModule`. The `McpSpringAiSchemaModule` is now used by `McpJsonSchemaGenerator` and specifically handles `@McpToolParam` annotations for MCP tools. The `McpJsonSchemaGenerator` also now correctly uses `Nullness.forParameter` to determine parameter nullability, improving robustness.\n\nUpdated `McpJsonSchemaGenerator.isMethodParameterRequired` to use `Nullness.forParameter` which provides a more robust way to detect nullability compared to checking for the `@Nullable` annotation directly.",
                    "The original code lacked comprehensive integration tests to validate the accuracy of JSON schema generation for MCP tool inputs, particularly concerning the `required` property. This deficiency could lead to incorrect tool schemas being generated, impacting how AI models interact with Spring AI tools. The new `McpToolInputSchemaIT` addresses this by providing a dedicated test suite that covers various scenarios of parameter nullability and nested structures. The refactoring of `SpringAiSchemaModule` into an abstract base class and concrete implementations improves code organization and maintainability, allowing for distinct schema generation logic for MCP tools versus general Spring AI tools. The adoption of `Nullness.forParameter` enhances the reliability of nullability detection, ensuring that schemas correctly reflect parameter intent, especially when using JSpecify annotations.");

            // log.info("step=interpretation");
            // InterpretationAgentOutput interpretation = interpretationAgent.execute(
            // new InterpretationAgentInput(codeChanges));
            // try {
            // log.info("interpretation result:\n{}",
            // objectMapper.writeValueAsString(interpretation));
            // } catch (Exception e) {
            // log.info("interpretation result: {}", interpretation, e);
            // }

            PlanningAgentOutput mockPlanningResult = new PlanningAgentOutput(
                    "The user wants a checklist for reviewing code changes related to MCP tool input schema generation and refactoring of schema modules. The checklist should focus on compatibility, design, security, functionality, performance, maintainability, and readability, adhering to the provided guidelines and review questions. The changes involve adding an integration test, refactoring a schema module into an abstract base class and concrete implementations, and improving nullability detection. The checklist items should be specific and actionable.",
                    List.of(
                            "Does McpToolInputSchemaIT adequately cover scenarios with nested records and various nullability annotations (@Nullable, @JsonProperty(required = false))?",
                            "Are the test cases in McpToolInputSchemaIT self-contained and representative of real-world MCP tool usage?",
                            "Does the refactoring into AbstractSpringAiSchemaModule, McpSpringAiSchemaModule, and SpringAiSchemaModule maintain existing functionality and improve code organization?",
                            "Does McpJsonSchemaGenerator correctly utilize Nullness.forParameter for robust nullability detection?",
                            "Is the McpSpringAiSchemaModule correctly configured and used by McpJsonSchemaGenerator to handle MCP-specific annotations like @McpToolParam?",
                            "Are the end-to-end tests in McpToolInputSchemaIT verifying the complete MCP tool registration and schema generation pipeline?",
                            "Does the test suite increase confidence in the correctness of JSON schema generation for MCP tool inputs, especially regarding the 'required' field?"));

            // log.info("step=planning");
            // PlanningAgentOutput planning = planningAgent.execute(
            // new PlanningAgentInput(codeChanges, interpretation, codeAnalysis));
            // try {
            // log.info("planning result:\n{}", objectMapper.writeValueAsString(planning));
            // } catch (Exception e) {
            // log.info("planning result: {}", planning, e);
            // }

            log.info("step=computation items={}", mockPlanningResult.checklistItems().size());
            List<ItemAnswer> itemAnswers = new ArrayList<>();
            for (String item : mockPlanningResult.checklistItems()) {
                log.debug("item={}", item);
                String answer = computationAgent.execute(new ComputationAgentInput(codeChanges, item));
                itemAnswers.add(new ItemAnswer(item, answer));
            }
            try {
                log.info("computation results:\n{}", objectMapper.writeValueAsString(itemAnswers));
            } catch (Exception e) {
                log.info("computation results: {}", itemAnswers, e);
            }

            log.info("step=summary");
            SummaryAgentOutput reviewResult = summaryAgent.execute(
                    new SummaryAgentInput(codeChanges, codeAnalysis, itemAnswers));
            try {
                log.info("summary result:\n{}", objectMapper.writeValueAsString(reviewResult));
            } catch (Exception e) {
                log.info("summary result: {}", reviewResult, e);
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
            log.info("report written to {}", reportPath.toAbsolutePath());
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
