package com.llmcr.runner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.llmcr.service.review.CodeReviewService;
import com.llmcr.service.review.CodeReviewService.ReviewRequest;

@Component
@ConditionalOnProperty(name = "app.mode", havingValue = "review")
public class CodeReviewRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CodeReviewRunner.class);

    private final CodeReviewService codeReviewService;

    public CodeReviewRunner(CodeReviewService codeReviewService) {
        this.codeReviewService = codeReviewService;
    }

    @Override
    public void run(String... args) {
        String report = codeReviewService.review(new ReviewRequest("../_datasets/test/example.diff", ""));

        log.info("Code review report generated:\n{}", report);
    }

}
