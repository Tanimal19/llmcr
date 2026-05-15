package com.llmcr.runner;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.llmcr.service.review.CodeReviewService;

@Component
@ConditionalOnProperty(name = "app.mode", havingValue = "review")
public class CodeReviewRunner implements ApplicationRunner {

    private final CodeReviewService codeReviewService;

    public CodeReviewRunner(CodeReviewService codeReviewService) {
        this.codeReviewService = codeReviewService;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        List<String> nonOptionArgs = args.getNonOptionArgs();

        if (args.containsOption("use-mock")) {
            codeReviewService.review(null, true);
            return;
        }
        if (nonOptionArgs.isEmpty()) {
            throw new IllegalArgumentException("No diff file path provided. Pass the path as a CLI argument.");
        }

        String diffFilePath = nonOptionArgs.get(0);
        codeReviewService.review(diffFilePath, false);
    }
}
