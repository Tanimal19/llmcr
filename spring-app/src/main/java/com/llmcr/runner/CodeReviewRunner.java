package com.llmcr.runner;

import com.llmcr.feature.review.CodeReviewService;
import com.llmcr.feature.review.CodeReviewService.CodeReviewInput;
import java.io.Console;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Scanner;
import java.util.stream.Stream;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.mode", havingValue = "review")
public class CodeReviewRunner implements ApplicationRunner {

  private final CodeReviewService codeReviewService;
  private final Scanner stdinScanner = new Scanner(System.in);

  public CodeReviewRunner(CodeReviewService codeReviewService) {
    this.codeReviewService = codeReviewService;
  }

  @Override
  public void run(ApplicationArguments args) throws Exception {
    List<String> nonOptionArgs = args.getNonOptionArgs();
    Integer jsonlIndex = parseJsonlIndex(args);
    boolean useMock = args.containsOption("use-mock");

    ReviewExecutionPlan plan = buildExecutionPlan(nonOptionArgs, jsonlIndex, useMock);
    confirmExecution(plan);
    executePlan(plan);
  }

  private ReviewExecutionPlan buildExecutionPlan(
      List<String> nonOptionArgs, Integer jsonlIndex, boolean useMock) {
    if (useMock) {
      if (!nonOptionArgs.isEmpty() || jsonlIndex != null) {
        throw new IllegalArgumentException(
            "--use-mock cannot be combined with file path or --jsonl-index");
      }
      return ReviewExecutionPlan.mock();
    }

    if (nonOptionArgs.isEmpty()) {
      return ReviewExecutionPlan.mock();
    }

    if (nonOptionArgs.size() > 1) {
      throw new IllegalArgumentException("Only one input file path is allowed in review mode.");
    }

    String inputFilePath = nonOptionArgs.get(0);
    String lowerCasePath = inputFilePath.toLowerCase(Locale.ROOT);

    if (lowerCasePath.endsWith(".json")) {
      if (jsonlIndex != null) {
        throw new IllegalArgumentException("--jsonl-index can only be used with .jsonl files");
      }
      return ReviewExecutionPlan.json(inputFilePath);
    }

    if (lowerCasePath.endsWith(".jsonl")) {
      if (jsonlIndex != null) {
        return ReviewExecutionPlan.jsonlAtIndex(inputFilePath, jsonlIndex);
      }

      int itemCount = countJsonlItems(inputFilePath);
      if (itemCount == 0) {
        throw new IllegalArgumentException("JSONL file has no items: " + inputFilePath);
      }
      return ReviewExecutionPlan.jsonlAll(inputFilePath, itemCount);
    }

    throw new IllegalArgumentException("Unsupported input file type. Use .json or .jsonl");
  }

  private void executePlan(ReviewExecutionPlan plan) {
    switch (plan.mode()) {
      case MOCK -> codeReviewService.execute(new CodeReviewInput(null, true));
      case JSON_FILE -> codeReviewService.execute(new CodeReviewInput(plan.inputFilePath(), false));
      case JSONL_SINGLE_INDEX ->
          codeReviewService.execute(
              new CodeReviewInput(plan.inputFilePath(), plan.jsonlIndex(), false));
      case JSONL_ALL_ITEMS -> {
        for (int i = 0; i < plan.jsonlItemCount(); i++) {
          System.out.printf("Running review for JSONL item %d/%d%n", i, plan.jsonlItemCount() - 1);
          codeReviewService.execute(new CodeReviewInput(plan.inputFilePath(), i, false));
        }
      }
    }
  }

  private void confirmExecution(ReviewExecutionPlan plan) {
    System.out.println();
    System.out.println("Review execution plan:");
    switch (plan.mode()) {
      case MOCK -> System.out.println("- Use mock review data");
      case JSON_FILE -> {
        System.out.println("- Input type: JSON file");
        System.out.println("- Input path: " + plan.inputFilePath());
      }
      case JSONL_SINGLE_INDEX -> {
        System.out.println("- Input type: JSONL single item");
        System.out.println("- Input path: " + plan.inputFilePath());
        System.out.println("- Index: " + plan.jsonlIndex());
      }
      case JSONL_ALL_ITEMS -> {
        System.out.println("- Input type: JSONL all items");
        System.out.println("- Input path: " + plan.inputFilePath());
        System.out.println("- Total items: " + plan.jsonlItemCount());
      }
    }

    String answer = readConfirmationAnswer();
    if (!isConfirmationAccepted(answer)) {
      throw new IllegalStateException("Review execution cancelled by user.");
    }
  }

  private String readConfirmationAnswer() {
    Console console = System.console();
    if (console != null) {
      return console.readLine("Proceed? [y/N]: ");
    }

    System.out.print("Proceed? [y/N]: ");
    if (stdinScanner.hasNextLine()) {
      return stdinScanner.nextLine();
    }
    return null;
  }

  private boolean isConfirmationAccepted(String answer) {
    if (answer == null) {
      return false;
    }

    String normalized = answer.trim().toLowerCase(Locale.ROOT);
    return normalized.equals("y") || normalized.equals("yes");
  }

  private int countJsonlItems(String inputFilePath) {
    Path path = Path.of(inputFilePath);
    if (!Files.isRegularFile(path)) {
      throw new IllegalArgumentException("JSONL file does not exist: " + inputFilePath);
    }

    try (Stream<String> lines = Files.lines(path)) {
      return (int) lines.count();
    } catch (IOException ex) {
      throw new IllegalArgumentException("Failed to read JSONL file: " + inputFilePath, ex);
    }
  }

  private Integer parseJsonlIndex(ApplicationArguments args) {
    List<String> values = args.getOptionValues("jsonl-index");
    String rawValue =
        Optional.ofNullable(values)
            .filter(list -> !list.isEmpty())
            .map(list -> list.get(0))
            .orElse(null);
    if (rawValue == null) {
      return null;
    }
    try {
      int parsedIndex = Integer.parseInt(rawValue);
      if (parsedIndex < 0) {
        throw new IllegalArgumentException("--jsonl-index must be >= 0");
      }
      return parsedIndex;
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("Invalid --jsonl-index value: " + rawValue, ex);
    }
  }

  private enum ReviewMode {
    MOCK,
    JSON_FILE,
    JSONL_SINGLE_INDEX,
    JSONL_ALL_ITEMS
  }

  private record ReviewExecutionPlan(
      ReviewMode mode, String inputFilePath, Integer jsonlIndex, int jsonlItemCount) {
    private static ReviewExecutionPlan mock() {
      return new ReviewExecutionPlan(ReviewMode.MOCK, null, null, 0);
    }

    private static ReviewExecutionPlan json(String inputFilePath) {
      return new ReviewExecutionPlan(ReviewMode.JSON_FILE, inputFilePath, null, 0);
    }

    private static ReviewExecutionPlan jsonlAtIndex(String inputFilePath, int jsonlIndex) {
      return new ReviewExecutionPlan(ReviewMode.JSONL_SINGLE_INDEX, inputFilePath, jsonlIndex, 0);
    }

    private static ReviewExecutionPlan jsonlAll(String inputFilePath, int jsonlItemCount) {
      return new ReviewExecutionPlan(
          ReviewMode.JSONL_ALL_ITEMS, inputFilePath, null, jsonlItemCount);
    }
  }
}
