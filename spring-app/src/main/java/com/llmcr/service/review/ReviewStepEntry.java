package com.llmcr.service.review;

import java.util.List;

public class ReviewStepEntry {
    String stepName;
    String parentStepName;
    String modelName;
    Long durationMs;
    Object input;
    List<String> messages;
    Object output;
}
