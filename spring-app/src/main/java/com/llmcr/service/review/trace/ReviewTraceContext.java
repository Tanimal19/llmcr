package com.llmcr.service.review.trace;

/**
 * A context holder for review trace collection. It uses ThreadLocal to store
 * the current ReviewTraceCollector for the ongoing review process.
 */
public final class ReviewTraceContext {

    private static final ThreadLocal<ReviewTraceCollector> HOLDER = new ThreadLocal<>();

    private ReviewTraceContext() {
    }

    public static void start(ReviewTraceCollector collector) {
        HOLDER.set(collector);
    }

    public static ReviewTraceCollector current() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
