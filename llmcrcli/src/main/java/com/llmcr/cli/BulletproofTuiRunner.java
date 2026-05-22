package com.llmcr.cli;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.Reader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class BulletproofTuiRunner implements CommandLineRunner {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void run(String... args) throws Exception {
        executor.submit(() -> {
            try {
                // 1. 初始化最核心的 Terminal
                Terminal terminal = TerminalBuilder.builder().system(true).build();
                PrintWriter writer = terminal.writer();

                writer.println("====== ⚡ 100% 穩定的自製 TUI 工具 ⚡ ======");
                writer.flush();

                boolean running = true;
                while (running) {
                    // 測試單選選單 (List)
                    List<String> mainMenu = Arrays.asList("管理使用者資料", "系統模組設定", "離開系統");
                    int choice = showListPrompt(terminal, "請選擇要執行的功能：", mainMenu);

                    switch (choice) {
                        case 0:
                            writer.println("\n[系統提示] 進入使用者管理邏輯...\n");
                            writer.flush();
                            break;
                        case 1:
                            // 測試多選選單 (Checkbox)
                            List<String> modules = Arrays.asList("權限校驗模組", "日誌審計模組", "第三方金流服務");
                            List<Integer> selected = showCheckboxPrompt(terminal, "請勾選要啟動的模組 (【空白鍵】勾選，【Enter】確認)：", modules);
                            writer.println("\n[配置成功] 最終勾選的索引為: " + selected + "\n");
                            writer.flush();
                            break;
                        case 2:
                            writer.println("\n正在安全退出系統...");
                            writer.flush();
                            running = false;
                            System.exit(0);
                            break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * 自製單選選單 (List Prompt)
     */
    private int showListPrompt(Terminal terminal, String message, List<String> options) throws Exception {
        // 進入 Raw Mode，讓 terminal 能夠即時捕捉單個按鍵，不需等使用者按 Enter
        terminal.enterRawMode();
        Reader reader = terminal.reader();
        PrintWriter writer = terminal.writer();

        int selected = 0;
        int size = options.size();
        writer.println(message);

        while (true) {
            // 渲染選單
            for (int i = 0; i < size; i++) {
                if (i == selected) {
                    writer.println("> \u001B[32m" + options.get(i) + "\u001B[0m"); // 綠色高亮
                } else {
                    writer.println("  " + options.get(i));
                }
            }
            writer.flush();

            // 讀取鍵盤訊號
            int ch = reader.read();

            // 關鍵：利用 ANSI Code 游標向上移動 N 行，準備重繪畫面（達成原地刷新的效果）
            writer.print("\033[" + size + "A");
            writer.flush();

            if (ch == 27) { // 捕捉方向鍵 (Escape sequence: ESC [ A 或 B)
                int n1 = reader.read();
                int n2 = reader.read();
                if (n1 == 91) {
                    if (n2 == 65) {        // 向上鍵
                        selected = (selected - 1 + size) % size;
                    } else if (n2 == 66) { // 向下鍵
                        selected = (selected + 1) % size;
                    }
                }
            } else if (ch == 10 || ch == 13) { // 捕捉 Enter 鍵
                // 把剛才渲染的選單行數用 ANSI 往下推，並還原畫面
                writer.print("\033[" + size + "B\r");
                writer.flush();
                return selected;
            }
        }
    }

    /**
     * 自製多選選單 (Checkbox Prompt)
     */
    private List<Integer> showCheckboxPrompt(Terminal terminal, String message, List<String> options) throws Exception {
        terminal.enterRawMode();
        Reader reader = terminal.reader();
        PrintWriter writer = terminal.writer();

        int current = 0;
        int size = options.size();
        boolean[] checked = new boolean[size];
        writer.println(message);

        while (true) {
            // 渲染多選選單
            for (int i = 0; i < size; i++) {
                String box = checked[i] ? "[\u001B[32m✔\u001B[0m]" : "[ ]";
                if (i == current) {
                    writer.println("> " + box + " \u001B[36m" + options.get(i) + "\u001B[0m"); // 藍色游標
                } else {
                    writer.println("  " + box + " " + options.get(i));
                }
            }
            writer.flush();

            int ch = reader.read();
            writer.print("\033[" + size + "A"); // 游標向上移，準備重繪
            writer.flush();

            if (ch == 27) { // 方向鍵
                int n1 = reader.read();
                int n2 = reader.read();
                if (n1 == 91) {
                    if (n2 == 65) current = (current - 1 + size) % size;
                    else if (n2 == 66) current = (current + 1) % size;
                }
            } else if (ch == 32) { // 空白鍵：切換勾選狀態
                checked[current] = !checked[current];
            } else if (ch == 10 || ch == 13) { // Enter 鍵：確認送出
                writer.print("\033[" + size + "B\r");
                writer.flush();

                List<Integer> results = new ArrayList<>();
                for (int i = 0; i < size; i++) {
                    if (checked[i]) results.add(i);
                }
                return results;
            }
        }
    }
}