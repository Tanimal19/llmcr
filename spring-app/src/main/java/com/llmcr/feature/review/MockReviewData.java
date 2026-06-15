package com.llmcr.feature.review;

import com.llmcr.feature.review.CodeReviewReport.InterpretationContent;
import com.llmcr.feature.review.agent.DraftingAgent.DraftingAgentOutput;
import java.util.List;

public final class MockReviewData {

  public static final String MOCK_PULL_REQUEST_JSON_PATH =
      "src/test/resources/example_pull_request.json";

  public static final InterpretationContent MOCK_INTERPRETATION_OUTPUT =
      new InterpretationContent(
          "The codebase was updated to improve how structured output schemas are handled during LLM calls. Key changes include: 1) Enhancing the `StructuredOutputConverter` interface to define a default `getJsonSchema()` method. 2) Updating `DefaultChatClient` to use this interface method directly, allowing non-`BeanOutputConverter` implementations to provide schema-based structured output. 3) Modifying `ChatModelCallAdvisor` to use `chatClientRequest.mutate()` for building augmented requests instead of manual map copying. 4) Updating unit tests to support custom `StructuredOutputConverter` implementations and verifying correct schema propagation.",
          "The original implementation tightly coupled native structured output support to the `BeanOutputConverter` class. By moving schema retrieval to the `StructuredOutputConverter` interface, the system now supports custom conversion logic (e.g., handling specific `JsonNode` outputs) while still being able to pass JSON schemas to the model. Additionally, using the mutation pattern in `ChatModelCallAdvisor` ensures a more consistent and robust way of updating request parameters.");

  public static final DraftingAgentOutput MOCK_DRAFTING_OUTPUT =
      new DraftingAgentOutput(
          List.of(
              new CodeReviewReport.IssueDraft(
                  "Maintainability",
                  "Minor",
                  "spring-ai-client-chat/src/main/java/org/springframework/ai/chat/client/DefaultChatClient.java::430",
                  "Redundant multiline formatting",
                  "The `Boolean.TRUE.equals(...)` check is split across multiple lines, reducing readability for a simple boolean condition.",
                  "Code style prefers logical conditions to be concise or on a single line if space permits."),
              new CodeReviewReport.IssueDraft(
                  "Design",
                  "Major",
                  "spring-ai-client-chat/src/main/java/org/springframework/ai/chat/client/advisor/ChatModelCallAdvisor.java::59",
                  "Use of mutable HashMap in advisor context",
                  "Replacing `Map.copyOf()` with `new HashMap<>(...)` allows the returned context to be mutated by downstream callers, potentially leading to side effects across the advisor chain.",
                  "The intention of the original `Map.copyOf()` was to ensure immutability and prevent side effects during the advisor call chain."),
              new CodeReviewReport.IssueDraft(
                  "Security",
                  "Minor",
                  "spring-ai-model/src/main/java/org/springframework/ai/converter/StructuredOutputConverter.java::35",
                  "Interface pollution with default method",
                  "Adding `getJsonSchema()` as a default method returning `null` adds a contract expectation that implementers must now handle as an optional feature. This can lead to NullPointerExceptions if consumers of the interface assume non-nullity.",
                  "Consumers of `StructuredOutputConverter` may not consistently check for null results from `getJsonSchema()`.")));
}
