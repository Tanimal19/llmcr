package com.llmcr.service.review;

import java.util.List;

import com.llmcr.agent.InterpretationAgent.InterpretationAgentOutput;
import com.llmcr.agent.PlanningAgent.PlanningAgentOutput;

public final class MockReviewData {
    public static final String MOCK_PULL_REQUEST_JSON_PATH = "src/test/resources/example_pull_request.json";

    public static final InterpretationAgentOutput MOCK_INTERPRETATION = new InterpretationAgentOutput(
            "The `BeanOutputConverter` now delegates JSON schema generation to `JsonSchemaGenerator`. This aligns structured output conversion with the JSON schema behavior used for tool calling, including OpenAPI format hints and default requiredness rules. The `postProcessSchema` extension point has been removed and replaced by overriding `generateSchema`.",
            "The original `BeanOutputConverter` had its own JSON schema generation logic which was not fully aligned with the `JsonSchemaGenerator` used for tool calls. This change centralizes schema generation, ensuring consistency. By delegating to `JsonSchemaGenerator`, it benefits from existing logic for handling various annotations, Kotlin specifics (optional properties are not required), and OpenAPI format hints for primitive types. The removal of `postProcessSchema` simplifies the API and encourages overriding the more comprehensive `generateSchema` method for customizations.");

    public static final PlanningAgentOutput MOCK_PLANNING = new PlanningAgentOutput(
            "The user wants a checklist for a code review based on the provided code changes and description. The changes focus on consolidating JSON schema generation logic within Spring AI. The core changes are in `BeanOutputConverter.java` and `JsonSchemaGenerator.java`, with tests and upgrade notes updated accordingly. The checklist should verify that the consolidation is done correctly, consistency with tool-calling schema generation is maintained, and the API changes are handled properly. I will create checklist items that cover these aspects, ensuring they are specific and actionable.",
            List.of(
                    "Does the change in `BeanOutputConverter` correctly delegate JSON schema generation to `JsonSchemaGenerator`?",
                    "Are OpenAPI-style format hints (e.g., `int32`) now included for primitive types in generated JSON schemas, consistent with tool calling behavior?",
                    "Have Kotlin optional properties (nullable or with default values) been correctly excluded from the JSON schema `required` array?",
                    "Are properties annotated with `@JsonProperty(required = false)` or where `required` is not specified, no longer treated as required in the generated schema?",
                    "Is the removal of the `BeanOutputConverter.postProcessSchema` extension point handled correctly, and are custom subclasses updated to override `generateSchema` instead?",
                    "Does the `ChatClientNativeStructuredResponseTests` reflect the expected schema changes, specifically the addition of `\"format\": \"int32\"`?",
                    "Does the upgrade notes accurately describe the impact and migration steps for the `BeanOutputConverter` changes, including the API change from `postProcessSchema` to `generateSchema`?"));
}
