package com.llmcr.service.review;

import java.util.List;

import com.llmcr.agent.InterpretationAgent.InterpretationAgentOutput;
import com.llmcr.agent.PlanningAgent.PlanningAgentOutput;

public final class MockReviewData {
    public static final InterpretationAgentOutput MOCK_INTERPRETATION = new InterpretationAgentOutput(
            "Introduced a new integration test class `McpToolInputSchemaIT` that verifies the correct generation of JSON schemas for MCP tool inputs. This test class specifically focuses on ensuring that the `required` field in the generated schema accurately reflects the nullability of method parameters and nested record fields, respecting annotations like `@Nullable` and `@JsonProperty(required = false)`. It also includes an end-to-end test to confirm that a tool with nested record parameters is correctly registered and has the expected schema within the MCP server context.\n\nRefactored `SpringAiSchemaModule` into an abstract base class `AbstractSpringAiSchemaModule` and created concrete implementations `McpSpringAiSchemaModule` and `SpringAiSchemaModule`. The `McpSpringAiSchemaModule` is now used by `McpJsonSchemaGenerator` and specifically handles `@McpToolParam` annotations for MCP tools. The `McpJsonSchemaGenerator` also now correctly uses `Nullness.forParameter` to determine parameter nullability, improving robustness.\n\nUpdated `McpJsonSchemaGenerator.isMethodParameterRequired` to use `Nullness.forParameter` which provides a more robust way to detect nullability compared to checking for the `@Nullable` annotation directly.",
            "The original code lacked comprehensive integration tests to validate the accuracy of JSON schema generation for MCP tool inputs, particularly concerning the `required` property. This deficiency could lead to incorrect tool schemas being generated, impacting how AI models interact with Spring AI tools. The new `McpToolInputSchemaIT` addresses this by providing a dedicated test suite that covers various scenarios of parameter nullability and nested structures. The refactoring of `SpringAiSchemaModule` into an abstract base class and concrete implementations improves code organization and maintainability, allowing for distinct schema generation logic for MCP tools versus general Spring AI tools. The adoption of `Nullness.forParameter` enhances the reliability of nullability detection, ensuring that schemas correctly reflect parameter intent, especially when using JSpecify annotations.");

    public static final PlanningAgentOutput MOCK_PLANNING = new PlanningAgentOutput(
            "The user wants a checklist for reviewing code changes related to MCP tool input schema generation and refactoring of schema modules. The checklist should focus on compatibility, design, security, functionality, performance, maintainability, and readability, adhering to the provided guidelines and review questions. The changes involve adding an integration test, refactoring a schema module into an abstract base class and concrete implementations, and improving nullability detection. The checklist items should be specific and actionable.",
            List.of(
                    "Does McpToolInputSchemaIT adequately cover scenarios with nested records and various nullability annotations (@Nullable, @JsonProperty(required = false))?",
                    "Are the test cases in McpToolInputSchemaIT self-contained and representative of real-world MCP tool usage?",
                    "Does the refactoring into AbstractSpringAiSchemaModule, McpSpringAiSchemaModule, and SpringAiSchemaModule maintain existing functionality and improve code organization?",
                    "Does McpJsonSchemaGenerator correctly utilize Nullness.forParameter for robust nullability detection?",
                    "Is the McpSpringAiSchemaModule correctly configured and used by McpJsonSchemaGenerator to handle MCP-specific annotations like @McpToolParam?",
                    "Are the end-to-end tests in McpToolInputSchemaIT verifying the complete MCP tool registration and schema generation pipeline?",
                    "Does the test suite increase confidence in the correctness of JSON schema generation for MCP tool inputs, especially regarding the 'required' field?"));
}
