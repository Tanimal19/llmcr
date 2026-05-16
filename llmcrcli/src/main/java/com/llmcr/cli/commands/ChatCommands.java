package com.llmcr.cli.commands;

import java.io.File;
import java.io.FileWriter;

import org.jline.reader.LineReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import com.llmcr.cli.services.IBackendService;

@ShellComponent
public class ChatCommands {

    @Autowired
    private IBackendService backendService;

    @Autowired
    @Lazy
    private LineReader lineReader;

    @ShellMethod(key = "chat", value = "進入與大型語言模型的互動聊天模式")
    public void chat() {
        System.out.println("Entering chat mode. Type 'exit' or 'quit' to return to main menu.");
        System.out.println("────────────────────────────────────────────────────────────────");

        while (true) {
            // 模仿 Ollama 的提示字元
            String input = lineReader.readLine(">>> ");

            if (input == null || input.trim().equalsIgnoreCase("exit") || input.trim().equalsIgnoreCase("quit")) {
                System.out.println("Leaving chat mode.");
                break;
            }

            if (input.trim().isEmpty()) {
                continue;
            }

            try {
                // 呼叫後端接頭
                String response = backendService.chat(input);
                System.out.println(response);
                System.out.println();
            } catch (Exception e) {
                System.out.println("❌ Error: " + e.getMessage());
            }
        }
    }

    @ShellMethod(key = "review", value = "執行 Code Review。用法: review [diff_filepath]")
    public void review(@ShellOption(value = "filePath") String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            System.out.println("❌ Error: File not found at " + filePath);
            return;
        }

        try {
            // 1. 模擬進度條動畫
            simulateProgressBar();

            // 2. 呼叫後端產生 Review 內容
            String reviewContent = backendService.generateReview(file);

            // 3. 寫入到產生的檔案 (.review.md)
            String outputFilePath = filePath + ".review.md";
            try (FileWriter writer = new FileWriter(outputFilePath)) {
                writer.write(reviewContent);
            }

            // 4. 印出成功訊息與 Preview
            System.out.println("✅ Review generated at " + outputFilePath);
            System.out.println("\nPreview:");
            System.out.println("────────────────────────");
            System.out.println(reviewContent);
            System.out.println();

        } catch (Exception e) {
            System.out.println("\n❌ Error message: " + e.getMessage());
        }
    }

    // 輔助方法：製作用戶要求的進度條動畫
    private void simulateProgressBar() throws InterruptedException {
        int total = 100;
        int barLength = 20;
        for (int i = 0; i <= total; i += 25) { // 每次跳 25% 模擬進度
            StringBuilder bar = new StringBuilder();
            int filledLength = (int) ((double) i / total * barLength);

            bar.append("█".repeat(filledLength));
            bar.append("░".repeat(barLength - filledLength));

            // \r 可以讓游標回到行首，達到刷新同一行的效果
            System.out.print("\rGenerating review: |" + bar + "| " + i + "%/100%");
            Thread.sleep(300); // 停頓模擬運算
        }
        System.out.println(); // 換行
    }
}
