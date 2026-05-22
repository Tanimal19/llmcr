package com.llmcr.cli.commands;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.shell.core.InputReader;
import org.springframework.shell.core.command.AbstractCommand;
import org.springframework.shell.core.command.CommandContext;
import org.springframework.shell.core.command.ExitStatus;

public class ChatCmd extends AbstractCommand {

    public ChatCmd() {
        super("chat", "LLMCR", "進入與大型語言模型的互動聊天模式");
    }

    /**
     * 模擬大模型打字機流式輸出 (Streaming) 效果
     */
    private void streamSimulation(String prompt, PrintWriter writer) {
        String mockResponse = "這是針對「" + prompt
                + "」的回覆。在 Spring Shell 4.0.1 中，利用 CommandContext 建立巢狀迴圈（Nested Loop）能完美重現 Ollama 的原生體驗。";
        for (char c : mockResponse.toCharArray()) {
            writer.print(c);
            writer.flush();
            try {
                Thread.sleep(15); // 模擬串流延遲
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public ExitStatus doExecute(CommandContext context) throws Exception {
        // 從 context 中直接取得標準輸入輸出工具
        PrintWriter writer = context.outputWriter();
        InputReader reader = context.inputReader();

        // 模擬 Ollama 經典啟動畫面
        // writer.println(">>> pulling manifest");
        // writer.println(">>> success");
        // writer.printf(">>> Entering continuous session with model: [%s]%n", model);
        writer.println(">>> Type '/exit' or 'exit' to return to main shell.\n");
        writer.flush(); // 4.0.1 中必須手動 flush 才會即時更新終端畫面

        // 用於存放當前對話歷史的記憶體（因為線程會持續卡在 while 迴圈中）
        List<String> chatHistory = new ArrayList<>();

        // 核心對話 REPL 迴圈
        while (true) {
            // 動態產生像 Ollama 的提示字元 ">>> llama3 > "
            String prompt = String.format(">>>");

            // 讀取使用者輸入
            String input = reader.readInput(prompt);

            // 檢查退出條件
            if (input == null || input.trim().equalsIgnoreCase("/exit") || input.trim().equalsIgnoreCase("exit")) {
                writer.println("Exiting chat session. Goodbye!");
                writer.flush();
                break;
            }

            if (input.trim().isEmpty()) {
                continue;
            }

            // 儲存上下文紀錄
            chatHistory.add("User: " + input);

            // 串接 AI 輸出（這裡可自行注入 Spring AI 的 ChatClient）
            writer.print("AI: ");
            streamSimulation(input, writer);
            writer.println("\n");
            writer.flush();
        }

        return ExitStatus.OK;
    }
}
