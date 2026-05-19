package com.llmcr.cli.commands;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jline.terminal.Terminal;
import org.jline.reader.LineReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import org.springframework.shell.standard.AbstractShellComponent;

// TUI 核心元件
import org.springframework.shell.component.view.TerminalUI;
import org.springframework.shell.component.view.control.ListView;
import org.springframework.shell.component.view.control.ListView.ItemStyle;
import org.springframework.shell.component.view.control.cell.AbstractListCell;
import org.springframework.shell.component.view.event.EventLoop;
import org.springframework.shell.component.view.event.KeyEvent.Key;
import org.springframework.shell.component.view.screen.Screen;
import org.springframework.shell.component.view.screen.Screen.Writer;
import org.springframework.shell.geom.Rectangle;
import org.springframework.shell.component.message.ShellMessageBuilder;

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
            "UserConfig");

    @ShellMethod(key = "chat", value = "進入與大型語言模型的互動聊天模式")
    public void chat() {
        System.out.println("Entering chat mode. Type 'exit' or 'quit' to return to main menu.");
        System.out.println("────────────────────────────────────────────────────────────────");

        while (true) {
            try {
                String input = lineReader.readLine(">>> ");

                if (input == null || input.trim().equalsIgnoreCase("exit") || input.trim().equalsIgnoreCase("quit")) {
                    System.out.println("Leaving chat mode.");
                    break;
                }

                if (input.trim().isEmpty()) {
                    continue;
                }

                String response = backendService.chat(input);
                System.out.println(response);
                System.out.println();

            } catch (Exception e) {
                System.out.println("\nLeaving chat mode.");
                break;
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
            simulateProgressBar();

            String reviewContent = backendService.generateReview(file);
            String outputFilePath = filePath + ".review.md";
            try (FileWriter writer = new FileWriter(outputFilePath)) {
                writer.write(reviewContent);
            }

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

            System.out.print("\rGenerating review: |" + bar + "| " + i + "%/100%");
            Thread.sleep(300);
        }
        System.out.println();
    }

    @ShellMethod(key = "sync", value = "同步資料庫")
    public void sync(@ShellOption(defaultValue = ".") String path) {
        System.out.println("Syncing database: |░░░░░░░░░░░░░░░░░░░░| 0%/100%");
        try {
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

    @ShellMethod(key = "lsdb", value = "瀏覽資料庫結構（純滾動模式）")
    public void lsdb() {
        TerminalUI ui = new TerminalUI(terminal);

        // 💡 調整 1：改用 NOCHECK 模式，這代表純瀏覽、不需要單選按 Enter 確定的效果
        ListView<String> listView = new ListView<>(ItemStyle.NOCHECK);
        listView.setItems(mockDbClasses);
        listView.setTitle("JavaClass / (↑↓ 鍵滾動, Q 退出)");

        ui.configure(listView);
        ui.setRoot(listView, true);

        EventLoop eventLoop = ui.getEventLoop();
        // 💡 調整 2：不攔截 Enter，只保留 Q 退出，讓使用者只能看和滾動
        eventLoop.keyEvents().subscribe(event -> {
            if (event.getPlainKey() == Key.q || event.getPlainKey() == Key.Q) {
                eventLoop.dispatch(ShellMessageBuilder.ofInterrupt());
            }
        });

        try {
            ui.run();
            System.out.println("\n[lsdb] 瀏覽結束。");
        } catch (Throwable t) {
            System.out.println("\n[lsdb] 操作已取消。");
        }
    }

    @ShellMethod(key = "setrag", value = "修改 RAG 影響範圍（多選模式）")
    public void setrag() {
        TerminalUI ui = new TerminalUI(terminal);

        // 使用原生官方的 ListView，不覆寫任何鍵盤綁定，確保原生 Space 勾選渲染正常
        ListView<String> listView = new ListView<>(ItemStyle.CHECKED);
        listView.setItems(mockDbClasses);
        listView.setTitle("JavaClass / (Space 勾選, Enter 確認, Q 退出)");

        // 💡 調整 3：建立一個我們自己用來收集打勾狀態的集合
        Set<String> checkedSet = new HashSet<>();

        // 💡 調整 4：利用官方文件提及的 CellFactory 機制，自訂 Cell 渲染
        // 當使用者按下 Space 時，ListView 會調用 c.setSelected(...) 更新儲存格狀態。
        // 我們在這個回呼裡實時攔截狀態，同步到我們的 checkedSet 中！
        listView.setCellFactory((list, item) -> new AbstractListCell<String>(item) {
            @Override
            public void setSelected(boolean selected) {
                super.setSelected(selected);
                if (selected) {
                    checkedSet.add(item);
                } else {
                    checkedSet.remove(item);
                }
            }

            @Override
            public void draw(Screen screen) {
                Rectangle rect = getRect();
                Writer writer = screen.writerBuilder().style(getStyle()).build();
                // 依據是否被選中，前方繪製核取方塊樣式 [x] 或 [ ]
                String prefix = isSelected() ? "[x] " : "[ ] ";
                writer.text(prefix + getItem(), rect.x(), rect.y());
                writer.background(rect, getBackgroundColor());
            }
        });

        ui.configure(listView);
        ui.setRoot(listView, true);

        List<String> selectedResult = new ArrayList<>();
        final boolean[] isConfirmed = new boolean[]{false};
        EventLoop eventLoop = ui.getEventLoop();

        eventLoop.keyEvents().subscribe(event -> {
            if (event.getPlainKey() == Key.Enter) {
                isConfirmed[0] = true;
                eventLoop.dispatch(ShellMessageBuilder.ofInterrupt());
            } else if (event.getPlainKey() == Key.q || event.getPlainKey() == Key.Q) {
                eventLoop.dispatch(ShellMessageBuilder.ofInterrupt());
            }
        });

        try {
            ui.run();
            if (isConfirmed[0]) {
                // 💡 調整 5：直接從我們即時同步的 checkedSet 撈資料，完全不需要反射與 get 方法！
                selectedResult.addAll(checkedSet);

                if (!selectedResult.isEmpty()) {
                    System.out.println("✅ 已成功更新 RAG 範圍: " + selectedResult);
                } else {
                    System.out.println("\n[setrag] 未選取任何項目。");
                }
            } else {
                System.out.println("\n[setrag] 操作已取消。");
            }
        } catch (Throwable t) {
            System.out.println("\n[setrag] 操作已取消。");
        }
    }
}