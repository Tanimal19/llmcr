package com.llmcr.runner;

import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.llmcr.service.review.CodeReviewService;

@Component
@ConditionalOnProperty(name = "app.mode", havingValue = "review")
public class CodeReviewRunner implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(CodeReviewRunner.class);

    private final CodeReviewService codeReviewService;

    public CodeReviewRunner(CodeReviewService codeReviewService) {
        this.codeReviewService = codeReviewService;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        List<String> nonOptionArgs = args.getNonOptionArgs();

        if (args.containsOption("use-mock")) {
            logger.info("Using mock data for code review");
            Path reportPath = codeReviewService.review(null, true);
            logger.info("Mock code review completed. Report written to: {}", reportPath.toAbsolutePath());
            return;
        }
        if (nonOptionArgs.isEmpty()) {
            logger.error("Usage: --app.mode=review <diff-file-path> [--use-mock]");
            throw new IllegalArgumentException("No diff file path provided. Pass the path as a CLI argument.");
        }

        String diffFilePath = nonOptionArgs.get(0);
        logger.info("Starting code review for diff file: {}", diffFilePath);

        Path reportPath = codeReviewService.review(diffFilePath, false);
        logger.info("Code review completed. Report written to: {}", reportPath.toAbsolutePath());
    }
}
