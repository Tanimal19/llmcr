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

    // 💡 建立一個乾淨的擴充類別，專門用來覆蓋被官方吞掉的 Enter 鍵
    public static class ActionListView extends ListView<String> {
        private final Runnable onEnter;

        public ActionListView(List<String> items, ItemStyle itemStyle, Runnable onEnter) {
            super(items, itemStyle);
            this.onEnter = onEnter;
        }

        @Override
        protected void initInternal() {
            super.initInternal(); // 1. 先保留官方原生的上下鍵與空白鍵邏輯

            // 2. 強行覆蓋官方的 Enter 鍵 (底層代碼 1048580)，讓它執行我們的結束確認邏輯
            this.registerKeyBinding(1048580, () -> {
                if (onEnter != null) {
                    onEnter.run();
                }
            });
        }
    }

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

        // 💡 調整 1：遵循需求，改用 NOCHECK 模式，這代表純瀏覽、沒有任何單選/多選按鈕的干擾
        ListView<String> listView = new ListView<>(mockDbClasses, ItemStyle.NOCHECK);
        listView.setTitle("JavaClass / (↑↓ 鍵滾動瀏覽, Q 退出)");

        ui.configure(listView);
        ui.setRoot(listView, true);

        EventLoop eventLoop = ui.getEventLoop();
        // 只保留 Q 退出，Enter 在此模式下預設不綁定任何破壞畫面的行為
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
        EventLoop eventLoop = ui.getEventLoop();

        Set<String> checkedSet = new HashSet<>();
        final boolean[] isConfirmed = new boolean[]{false};

        // 💡 調整 2：使用我們自訂的 ActionListView，並在建構子直接傳入當 Enter 被按下時要執行的邏輯
        ActionListView listView = new ActionListView(mockDbClasses, ItemStyle.CHECKED, () -> {
            isConfirmed[0] = true;
            eventLoop.dispatch(ShellMessageBuilder.ofInterrupt()); // 成功中斷並退出 UI 迴圈！
        });

        // 💡 調整 3：搭配 CellFactory 即時連動勾選狀態，這讓原生空白鍵完美工作
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
                String prefix = isSelected() ? "[x] " : "[ ] ";
                writer.text(prefix + getItem(), rect.x(), rect.y());
                writer.background(rect, getBackgroundColor());
            }
        });

        ui.configure(listView);
        ui.setRoot(listView, true);

        // 依然保留 Q 鍵作為隨時取消的手段
        eventLoop.keyEvents().subscribe(event -> {
            if (event.getPlainKey() == Key.q || event.getPlainKey() == Key.Q) {
                eventLoop.dispatch(ShellMessageBuilder.ofInterrupt());
            }
        });

        try {
            ui.run();
            if (isConfirmed[0]) {
                List<String> selectedResult = new ArrayList<>(checkedSet);
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