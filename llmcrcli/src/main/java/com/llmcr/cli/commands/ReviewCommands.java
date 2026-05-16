// src/main/java/com/example/clitool/commands/ReviewCommands.java
package com.llmcr.cli.commands;

// import com.llmcr.service.ReviewService; // 你/他人實作的後端
// import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

@ShellComponent
public class ReviewCommands {

    // @Autowired
    // private ReviewService reviewService; // 後端服務

    @ShellMethod(key = "review", value = "執行 Code Review")
    public void review(
            @ShellOption(value = {"", "--file"}, help = "diff 檔案路徑") String diffFilepath) {

        if (diffFilepath == null || diffFilepath.isBlank()) {
            System.out.println("❌ 請提供 diff 檔案路徑，例如: review ./commit-9527.diff");
            return;
        }

        System.out.println("> review " + diffFilepath);
        System.out.print("Generating review: ");

        try {
            // 呼叫後端（支援進度回調）
            // String resultPath = reviewService.performReview(diffFilepath, progress -> {
            //     // 簡單進度條（可替換成 Spring Shell 的 ProgressView）
            //     System.out.print("\rGenerating review: |");
            //     int bars = progress / 5;
            //     System.out.print("█".repeat(bars));
            //     System.out.print("░".repeat(20 - bars));
            //     System.out.print("| " + progress + "%/100%");
            // });

            // System.out.println("\n✅ Review generated at " + resultPath);

            // Preview
            System.out.println("\nPreview:");
            System.out.println("────────────────────────");
            // String preview = reviewService.getPreview(resultPath);
            // System.out.println(preview);

        } catch (Exception e) {
            System.out.println("\n❌ Error: " + e.getMessage());
        }
    }
}
