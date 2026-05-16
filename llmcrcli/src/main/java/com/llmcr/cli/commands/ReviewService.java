package com.llmcr.cli.commands;

public interface ReviewService {
    String performReview(String diffPath, java.util.function.Consumer<Integer> progressCallback);
    String getPreview(String reviewPath);
}