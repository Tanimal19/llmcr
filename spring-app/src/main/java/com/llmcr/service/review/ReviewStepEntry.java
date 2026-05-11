package com.llmcr.service.review;

import java.util.List;

public class ReviewStepEntry {
    String stepName;
    String parentStepName;
    String modelName;
    Object input;
    List<String> messages;
    Object output;
}
