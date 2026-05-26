package com.llmcr.infrastructure.agent.logging;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

public final class AgentContextHolder {

    private static final ThreadLocal<Deque<AgentCallContext>> CONTEXT_STACK = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Deque<Integer>> ITERATION_MARKERS = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Deque<AgentCallContext>> ITERATION_STACK = ThreadLocal
            .withInitial(ArrayDeque::new);
    private static volatile Consumer<AgentCallContext> onContextFinished;

    private AgentContextHolder() {
    }

    public static void setOnContextFinished(Consumer<AgentCallContext> callback) {
        onContextFinished = callback;
    }

    public static void beginContext(String agentName, String modelName) {
        AgentCallContext entry = new AgentCallContext();
        entry.agentName = agentName;
        entry.modelName = modelName;
        entry.startedAt = System.currentTimeMillis();
        CONTEXT_STACK.get().push(entry);
        ITERATION_MARKERS.get().push(ITERATION_STACK.get().size());
    }

    public static AgentCallContext endContext() {
        Deque<AgentCallContext> contextStack = CONTEXT_STACK.get();
        if (contextStack.isEmpty()) {
            return null;
        }

        AgentCallContext finished = contextStack.pop();
        finished.finish();

        AgentCallContext parent = contextStack.peek();
        if (parent != null) {
            parent.addIteration(finished);
        }

        Deque<Integer> iterationMarkers = ITERATION_MARKERS.get();
        if (!iterationMarkers.isEmpty()) {
            int targetDepth = iterationMarkers.pop();
            Deque<AgentCallContext> iterationStack = ITERATION_STACK.get();
            while (iterationStack.size() > targetDepth) {
                iterationStack.pop();
            }
        }

        // If this is the outermost context completion, trigger callback
        boolean isOutermostContext = contextStack.isEmpty();
        if (isOutermostContext && onContextFinished != null) {
            onContextFinished.accept(finished);
        }

        cleanupIfEmpty();
        return finished;
    }

    public static AgentCallContext currentContext() {
        return CONTEXT_STACK.get().peek();
    }

    public static void beginIteration(Object input) {
        AgentCallContext currentContext = currentContext();
        if (currentContext == null) {
            return;
        }

        AgentCallContext iteration = new AgentCallContext();
        iteration.agentName = currentContext.agentName;
        iteration.modelName = currentContext.modelName;
        iteration.input = input;
        iteration.startedAt = System.currentTimeMillis();
        ITERATION_STACK.get().push(iteration);
    }

    public static void completeIteration(Object output) {
        Deque<AgentCallContext> iterationStack = ITERATION_STACK.get();
        if (iterationStack.isEmpty()) {
            return;
        }

        AgentCallContext iteration = iterationStack.pop();
        iteration.output = output;
        iteration.finish();

        AgentCallContext currentContext = currentContext();
        if (currentContext != null) {
            currentContext.addIteration(iteration);
        }

        cleanupIfEmpty();
    }

    private static void cleanupIfEmpty() {
        if (CONTEXT_STACK.get().isEmpty()) {
            CONTEXT_STACK.remove();
        }
        if (ITERATION_MARKERS.get().isEmpty()) {
            ITERATION_MARKERS.remove();
        }
        if (ITERATION_STACK.get().isEmpty()) {
            ITERATION_STACK.remove();
        }
    }
}
