package com.llmcr.cli.commands;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jline.terminal.Terminal;
import org.jline.reader.LineReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import org.springframework.shell.component.MultiItemSelector;
import org.springframework.shell.component.MultiItemSelector.MultiItemSelectorContext;
import org.springframework.shell.component.SingleItemSelector;
import org.springframework.shell.component.SingleItemSelector.SingleItemSelectorContext;
import org.springframework.shell.component.support.SelectorItem;
import org.springframework.shell.standard.AbstractShellComponent;

import com.llmcr.cli.services.IBackendService;

@ShellComponent
public class ChatCommands extends AbstractShellComponent {

    @Autowired
    private Terminal terminal;

    @Autowired
    private IBackendService backendService;

    @Autowired
    @Lazy
    private LineReader lineReader;

    private final List<String> mockDbClasses = Arrays.asList(
            "ClassNodeExtractor",
            "DataSource",
            "ClassNode         (unsynced)",
            "UserConfig"
    );

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

    private void simulateProgressBar() throws InterruptedException {
        int total = 100;
        int barLength = 20;
        for (int i = 0; i <= total; i += 25) {
            StringBuilder bar = new StringBuilder();
            int filledLength = (int) ((double) i / total * barLength);

            bar.append("█".repeat(filledLength));
            bar.append("░".repeat(barLength - filledLength));

            // \r 可以讓游標回到行首，達到刷新同一行的效果
            System.out.print("\rGenerating review: |" + bar + "| " + i + "%/100%");
            Thread.sleep(300);
        }
        System.out.println();
    }

    @ShellMethod(key = "sync", value = "同步資料庫")
    public void sync(@ShellOption(defaultValue = ".") String path) {
        System.out.println("Syncing database: |░░░░░░░░░░░░░░░░░░░░| 0%/100%");
        try {
            // 模擬進度條
            for (int i = 0; i <= 100; i += 25) {
                String bar = "█".repeat(i / 5) + "░".repeat(20 - (i / 5));
                System.out.print("\rSyncing database: |" + bar + "| " + i + "%/100%");
                Thread.sleep(300);
            }
            System.out.println("\n✅ Sync Complete.\n");
        } catch (InterruptedException e) {
            System.out.println("\n❌ Sync Interrupted.");
        }
    }

    @ShellMethod(key = "lsdb", value = "瀏覽資料庫結構（單選/瀏覽模式）")
    public void lsdb() {
        List<SelectorItem<String>> items = mockDbClasses.stream()
                .map(name -> SelectorItem.of(name, name))
                .collect(Collectors.toList());

        SingleItemSelector<String, SelectorItem<String>> component =
                new SingleItemSelector<>(terminal, items, "JaveClass/", null);

        component.setResourceLoader(getResourceLoader());
        component.setTemplateExecutor(getTemplateExecutor());
        component.setMaxItems(10);

        try {
            SingleItemSelectorContext<String, SelectorItem<String>> context =
                    component.run(SingleItemSelectorContext.empty());

            Optional<SelectorItem<String>> resultItem = context.getResultItem();
            resultItem.ifPresent(item -> System.out.println("Selected: " + item.getItem()));

        } catch (Throwable t) {
            // 關鍵修改：捕獲 Throwable（包含 IOError），防止 Ctrl-C 導致 JVM 崩潰
            System.out.println("\n[lsdb] 操作已取消。");
        }
    }

    @ShellMethod(key = "setrag", value = "修改 RAG 影響範圍（多選模式）")
    public void setrag() {
        List<SelectorItem<String>> items = mockDbClasses.stream()
                .map(name -> SelectorItem.of(name, name))
                .collect(Collectors.toList());

        MultiItemSelector<String, SelectorItem<String>> component =
                new MultiItemSelector<>(terminal, items, "JaveClass/", null);

        component.setResourceLoader(getResourceLoader());
        component.setTemplateExecutor(getTemplateExecutor());
        component.setMaxItems(10);

        try {
            MultiItemSelectorContext<String, SelectorItem<String>> context =
                    component.run(MultiItemSelectorContext.empty());

            List<String> selectedItems = context.getResultItems().stream()
                    .map(SelectorItem::getItem)
                    .collect(Collectors.toList());

            System.out.println("✅ 已成功更新 RAG 範圍: " + selectedItems);

        } catch (Throwable t) {
            // 關鍵修改：捕獲 Throwable（包含 IOError），防止 Ctrl-C 導致 JVM 崩潰
            System.out.println("\n[setrag] 操作已取消。");
        }
    }
}