package com.llmcr.feature.review;

import com.llmcr.feature.review.CodeReviewReport.InterpretationContent;
import com.llmcr.feature.review.agent.PlanningAgent.PlanningAgentOutput;
import java.util.List;

public final class MockReviewData {

    public static final String MOCK_PULL_REQUEST_JSON_PATH = "src/test/resources/example_pull_request.json";

    public static final InterpretationContent MOCK_INTERPRETATION_OUTPUT = new InterpretationContent(
            "The `BeanOutputConverter` class has been refactored to utilize the `JsonSchemaGenerator` for generating JSON schemas, replacing its previous direct use of the `jsonschema-generator` library. Additionally, the `UserEntity` record in `ChatClientNativeStructuredResponseTests` now includes `format: \\\"int32\\\"` for the `age` field.",
            "The refactoring of `BeanOutputConverter` to use `JsonSchemaGenerator` aims to consolidate schema generation logic within the Spring AI project, promoting consistency and reducing dependencies on external libraries. The `JsonSchemaGenerator` is specifically tailored for Spring AI's needs, including support for annotations like `@ToolParam`. The addition of `format: \\\"int32\\\"` to the `age` field in `UserEntity` in `ChatClientNativeStructuredResponseTests` is a minor correction to ensure schema accuracy for integer types, aligning with common practices and potential LLM expectations.");

    public static final PlanningAgentOutput MOCK_PLANNING_OUTPUT = new PlanningAgentOutput(
            "The user wants a checklist for a code review. I need to analyze the provided code changes, change description, and static analysis outputs. The changes involve refactoring `BeanOutputConverter` to use a new `JsonSchemaGenerator` and a minor schema update in a test. The review guidelines cover compatibility, design, security, functionality, performance, maintainability, and readability. I should formulate concise questions focusing on these aspects, particularly around the refactoring, new utility class, annotation support, test coverage, and static analysis findings.",
            List.of(
                    "Does the refactoring in `BeanOutputConverter` to use `JsonSchemaGenerator` maintain compatibility with existing schema generation requirements and annotations like `@ToolParam`?",
                    "Are all necessary `jsonschema-generator` library dependencies correctly removed or replaced by the new `JsonSchemaGenerator` utility?",
                    "Does the `JsonSchemaGenerator.generateForType` method handle all expected Java types and annotations correctly, especially those related to JSON schema properties like `required`, `description`, and `format`?",
                    "Is the addition of `\"format\": \"int32\"` to the `age` field in `ChatClientNativeStructuredResponseTests` a deliberate and correct choice for integer schema representation in this context?",
                    "Have the `BeanOutputConverterTest` and `KotlinBeanOutputConverterTests` been updated to adequately cover the new `JsonSchemaGenerator` usage and any potential behavioral changes?",
                    "Are there any new `pmd` or `checkstyle` violations introduced by the changes, specifically in `BeanOutputConverter.java` and `JsonSchemaGenerator.java`?",
                    "Does the refactoring in `BeanOutputConverter` properly handle potential exceptions during schema generation, particularly from `this.jsonMapper.readTree(schemaString)`?"));
}
