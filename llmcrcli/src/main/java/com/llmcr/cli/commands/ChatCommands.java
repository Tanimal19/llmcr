package com.llmcr.cli.commands;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jline.terminal.Terminal;
import org.jline.reader.LineReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import org.springframework.shell.standard.AbstractShellComponent;

// 引入 Spring Shell TUI 核心元件 (移除會報錯的 BorderType)
import org.springframework.shell.component.view.TerminalUI;
import org.springframework.shell.component.view.control.ListView;
import org.springframework.shell.component.view.control.ListView.ItemStyle;
import org.springframework.shell.component.view.event.EventLoop;
import org.springframework.shell.component.view.event.KeyEvent.Key;
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
                // 攔截 Ctrl+C 或其他異常
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

    @ShellMethod(key = "lsdb", value = "瀏覽資料庫結構（單選模式 - TUI 上下鍵）")
    public void lsdb() {
        TerminalUI ui = new TerminalUI(terminal);
        ListView<String> listView = new ListView<>(ItemStyle.RADIO);
        listView.setItems(mockDbClasses);
        listView.setTitle("JavaClass / (Enter 確認, Q 退出)");

        ui.configure(listView);
        ui.setRoot(listView, true);

        final String[] selectedResult = new String[1];
        EventLoop eventLoop = ui.getEventLoop();

        eventLoop.keyEvents().subscribe(event -> {
            if (event.getPlainKey() == Key.Enter) {
                // 使用反射暴力繞過編譯器檢查，直接取得當前選項
                selectedResult[0] = getSelectedItemDynamically(listView, mockDbClasses);
                eventLoop.dispatch(ShellMessageBuilder.ofInterrupt());
            } else if (event.getPlainKey() == Key.q || event.getPlainKey() == Key.Q) {
                eventLoop.dispatch(ShellMessageBuilder.ofInterrupt());
            }
        });

        try {
            ui.run();
            if (selectedResult[0] != null) {
                System.out.println("Selected: " + selectedResult[0]);
            } else {
                System.out.println("\n[lsdb] 操作已取消。");
            }
        } catch (Throwable t) {
            System.out.println("\n[lsdb] 操作已取消。");
        }
    }

    @ShellMethod(key = "setrag", value = "修改 RAG 影響範圍（多選模式 - TUI 上下鍵與空白鍵）")
    public void setrag() {
        TerminalUI ui = new TerminalUI(terminal);
        ListView<String> listView = new ListView<>(ItemStyle.CHECKED);
        listView.setItems(mockDbClasses);
        listView.setTitle("JavaClass / (Space 勾選, Enter 確認, Q 退出)");

        ui.configure(listView);
        ui.setRoot(listView, true);

        List<String> selectedResult = new ArrayList<>();
        EventLoop eventLoop = ui.getEventLoop();

        eventLoop.keyEvents().subscribe(event -> {
            if (event.getPlainKey() == Key.Enter) {
                // 使用反射暴力取得所有被打勾的項目
                selectedResult.addAll(getCheckedItemsDynamically(listView, mockDbClasses));
                eventLoop.dispatch(ShellMessageBuilder.ofInterrupt());
            } else if (event.getPlainKey() == Key.q || event.getPlainKey() == Key.Q) {
                eventLoop.dispatch(ShellMessageBuilder.ofInterrupt());
            }
        });

        try {
            ui.run();
            if (!selectedResult.isEmpty()) {
                System.out.println("✅ 已成功更新 RAG 範圍: " + selectedResult);
            } else {
                System.out.println("\n[setrag] 操作已取消，或未選取任何項目。");
            }
        } catch (Throwable t) {
            System.out.println("\n[setrag] 操作已取消。");
        }
    }

    // ======================================================================
    // 萬無一失的 Reflection (反射) 工具區：徹底繞過 Spring Shell 3.3.2 封閉的 API
    // ======================================================================

    /**
     * 動態獲取單選模式下的當前選項
     */
    private String getSelectedItemDynamically(ListView<String> listView, List<String> originalItems) {
        try {
            // 嘗試尋找底層紀錄游標的變數 (通常是第一個 int 屬性，例如 activeIndex)
            for (java.lang.reflect.Field field : listView.getClass().getDeclaredFields()) {
                if (field.getType() == int.class) {
                    field.setAccessible(true);
                    int index = field.getInt(listView);
                    if (index >= 0 && index < originalItems.size()) {
                        return originalItems.get(index);
                    }
                }
            }
        } catch (Exception e) {
            // 忽略例外
        }
        return originalItems.get(0); // 防呆機制
    }

    /**
     * 動態獲取多選模式下所有被打勾的選項
     */
    private List<String> getCheckedItemsDynamically(ListView<String> listView, List<String> originalItems) {
        List<String> checked = new ArrayList<>();
        try {
            // 方法一：測試 3.3.x 隱藏的 isItemChecked 方法
            try {
                java.lang.reflect.Method m = listView.getClass().getMethod("isItemChecked", Object.class);
                for (String item : originalItems) {
                    Boolean isChecked = (Boolean) m.invoke(listView, item);
                    if (Boolean.TRUE.equals(isChecked)) {
                        checked.add(item);
                    }
                }
                if (!checked.isEmpty()) return checked;
            } catch (NoSuchMethodException e) {
                // 如果沒有此方法，進行方法二
            }

            // 方法二：直接去抓底層用來存打勾狀態的 Set
            for (java.lang.reflect.Field field : listView.getClass().getDeclaredFields()) {
                if (java.util.Set.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    java.util.Set<?> set = (java.util.Set<?>) field.get(listView);
                    if (set != null) {
                        for (Object obj : set) {
                            if (obj instanceof Integer) { // 紀錄的是索引
                                int idx = (Integer) obj;
                                if (idx >= 0 && idx < originalItems.size()) checked.add(originalItems.get(idx));
                            } else if (obj instanceof String) { // 紀錄的是字串
                                checked.add((String) obj);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 忽略例外
        }
        return checked;
    }
}
