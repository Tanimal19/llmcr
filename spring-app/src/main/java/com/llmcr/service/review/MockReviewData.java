package com.llmcr.service.review;

import java.util.List;

import com.llmcr.agent.InterpretationAgent.InterpretationAgentOutput;
import com.llmcr.agent.PlanningAgent.PlanningAgentOutput;

public final class MockReviewData {
    public static final String MOCK_PULL_REQUEST_JSON_PATH = "src/test/resources/example_pull_request.json";

    public static final InterpretationAgentOutput MOCK_INTERPRETATION = new InterpretationAgentOutput(
            "The `WebFluxSseServerTransportProvider.java` file has been updated to correctly handle session IDs for SSE connections. The `WebFluxMcpSessionTransport` class now has a `sessionId` field and a `setSessionId` method. The `handleSseConnection` method now calls `sessionTransport.setSessionId(sessionId)` after creating a new session, ensuring that the session ID is associated with the transport. Additionally, the `sendMessage` method in `WebFluxMcpSessionTransport` now logs the session ID when an error occurs during message sending. The `StructuredOutputValidationAdvisor.java` file has been updated to handle multiple results in `ChatClientResponse`. The validation logic now iterates through all results in `chatClientResponse.chatResponse().getResults()` and validates each one. If any result fails validation, the method returns immediately with the validation error. If all results are valid, it returns `SchemaValidation.passed()`.",
            "The motivation for these changes is to improve the robustness and error handling of the WebFlux SSE server transport and the structured output validation advisor. In `WebFluxSseServerTransportProvider`, not associating a session ID with the `WebFluxMcpSessionTransport` could lead to difficulties in debugging and error reporting, especially in scenarios with multiple concurrent connections. By adding the `sessionId` field and setting it correctly, error logs will now include the relevant session ID, making it easier to pinpoint issues. In `StructuredOutputValidationAdvisor`, the original implementation assumed that `chatClientResponse.chatResponse().getResult()` would always be present and contain the output. However, `ChatClientResponse` can contain multiple results, and the original code only considered the first one or assumed a single result structure. The updated code iterates through all results, ensuring that validation is performed on all parts of the response that are expected to be structured JSON. This makes the validation more comprehensive and reliable.");

    public static final PlanningAgentOutput MOCK_PLANNING = new PlanningAgentOutput(
            "The user wants a JSON checklist for code review based on the provided code changes and descriptions. I need to create 5-8 concise questions focusing on specific aspects like compatibility, design, functionality, and maintainability. The changes involve improving error handling and robustness in two specific areas: SSE server transport and structured output validation. I will focus my questions on verifying the implementation details and the described improvements.",
            List.of(
                    "Does the `WebFluxMcpSessionTransport` correctly associate the `sessionId` when the transport is created within `handleSseConnection`?",
                    "Is the `sessionId` field in `WebFluxMcpSessionTransport` marked as `volatile` to ensure visibility across threads?",
                    "Does the error logging in `WebFluxMcpSessionTransport.sendMessage` now include the `sessionId` for better debugging?",
                    "Does the `StructuredOutputValidationAdvisor` correctly handle cases where `chatClientResponse.chatResponse().getResults()` might be null or empty?",
                    "Is the validation logic in `StructuredOutputValidationAdvisor` correctly iterating through all results and returning early if any validation fails?",
                    "Does the `StructuredOutputValidationAdvisor` return `SchemaValidation.passed()` when all results are successfully validated?"));
}
