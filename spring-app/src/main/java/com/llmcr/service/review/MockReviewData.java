package com.llmcr.service.review;

import java.util.List;

import com.llmcr.agent.InterpretationAgent.InterpretationAgentOutput;
import com.llmcr.agent.PlanningAgent.PlanningAgentOutput;

public final class MockReviewData {
    public static final InterpretationAgentOutput MOCK_INTERPRETATION = new InterpretationAgentOutput(
            "The `ChatClientAutoConfiguration` now injects a `ToolCallingManager` bean into the `ChatClient.Builder`. This ensures that when a `ChatClient` is auto-configured, it correctly receives the `ToolCallingManager` if one is available in the application context, facilitating tool calling capabilities.",
            "Previously, the `ChatClient.Builder` did not explicitly accept a `ToolCallingManager`. This change aligns the auto-configuration with the `ChatClient.Builder`'s constructor signature, which was updated to accept a `ToolCallingManager`. This provides a centralized way to manage tool execution, especially when using the `ChatClient`'s advisor-based tool calling feature, rather than relying on internal model implementations. The test cases were updated to verify the correct injection of the `ToolCallingManager` and to handle potential ambiguity with multiple `ToolCallingManager` beans.");

    public static final PlanningAgentOutput MOCK_PLANNING = new PlanningAgentOutput(
            "The user wants a code review checklist based on the provided code changes. The changes focus on integrating `ToolCallingManager` into `ChatClient` auto-configuration and deprecating internal tool execution in `ChatModel` implementations. The checklist should cover the following aspects:\\n\\n1.  **Auto-configuration:** Verify correct injection of `ToolCallingManager` into `ChatClient.Builder`.\\n2.  **Testing:** Ensure updated tests cover `ToolCallingManager` injection and ambiguity.\\n3.  **Deprecation:** Check if `ChatModel` implementations correctly deprecate internal tool execution and warn users to switch to `ChatClient`.\\n4.  **API Changes:** Confirm `ChatClient.builder()` and related classes (like `DefaultChatClientBuilder`) are updated for `ToolCallingManager`.\\n5.  **Advisor Logic:** Verify the new `ToolCallAdvisor` logic, including its implementation of `ToolCallHandlingAdvisor` and the new `AdvisorParams`.\\n6.  **Integration Tests:** Ensure tests validate the new auto-registration and behavior of `ToolCallAdvisor`.\\n\\nI will structure the checklist to cover these points concisely.",
            List.of(
                    "Does the `ChatClientAutoConfiguration` correctly inject `ToolCallingManager` into the `ChatClient.Builder` constructor?",
                    "Are the updated test cases in `ChatClientAutoConfigurationTests.java` comprehensive in verifying `ToolCallingManager` injection and ambiguity handling?",
                    "Do the modified `ChatModel` classes (AnthropicChatModel, BedrockProxyChatModel, etc.) correctly deprecate and warn about the internal tool execution path when `ToolCallingManager` is used internally, shifting focus to `ChatClient` and `ToolCallAdvisor`?",
                    "Is the `ChatClient.builder()` method updated to accept `ToolCallingManager` as an optional parameter?",
                    "Has the `ChatClient.DefaultChatClientBuilder` been updated to correctly pass the `ToolCallingManager` to the `DefaultChatClientRequestSpec`?",
                    "Is the `ToolCallAdvisor` now implementing `ToolCallHandlingAdvisor` to prevent duplicate auto-registration?",
                    "Are new `AdvisorParams` for `toolCallAdvisorAutoRegister` and `toolCallAdvisorOrder` correctly implemented and utilized?",
                    "Do the integration tests (e.g., `OpenAIToolCallAdvisorAutoRegistrationIT.java`) verify the auto-registration and functional behavior of `ToolCallAdvisor` with different `ChatModel` implementations?"));
}
