package com.llmcr.feature.review;

import com.llmcr.feature.review.CodeReviewReport.InterpretationContent;
import com.llmcr.feature.review.agent.DraftingAgent.DraftingAgentOutput;
import java.util.List;

public final class MockReviewData {

  public static final String MOCK_PULL_REQUEST_JSON_PATH =
      "src/test/resources/example_pull_request.json";

  public static final InterpretationContent MOCK_INTERPRETATION_OUTPUT =
      new InterpretationContent(
          "The `BeanOutputConverter` class has been refactored to utilize the `JsonSchemaGenerator` for generating JSON schemas, replacing its previous direct use of the `jsonschema-generator` library. Additionally, the `UserEntity` record in `ChatClientNativeStructuredResponseTests` now includes `format: \\\"int32\\\"` for the `age` field.",
          "The refactoring of `BeanOutputConverter` to use `JsonSchemaGenerator` aims to consolidate schema generation logic within the Spring AI project, promoting consistency and reducing dependencies on external libraries. The `JsonSchemaGenerator` is specifically tailored for Spring AI's needs, including support for annotations like `@ToolParam`. The addition of `format: \\\"int32\\\"` to the `age` field in `UserEntity` in `ChatClientNativeStructuredResponseTests` is a minor correction to ensure schema accuracy for integer types, aligning with common practices and potential LLM expectations.");

  public static final DraftingAgentOutput MOCK_DRAFTING_OUTPUT = new DraftingAgentOutput(List.of());
}
