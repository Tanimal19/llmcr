package com.llmcr.cli.commands;


// import com.llmcr.service.ChatService;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
// import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import com.llmcr.cli.services.CommandListService;

import java.io.IOException;

@ShellComponent
public class ChatCommands {

    // @Autowired
    // private ChatService chatService; // 後端：處理 Ollama 對話

    @ShellMethod(key = "chat", value = "進入與 LM 的互動聊天模式 (輸入 exit 或 /quit 離開)")
    public void chat() throws IOException {
        System.out.println("🤖 進入聊天模式 (輸入 'exit' 或 '/quit' 回到主指令列表)");
        System.out.println("=".repeat(60));

        Terminal terminal = TerminalBuilder.builder().build();
        LineReader lineReader = LineReaderBuilder.builder()
                .terminal(terminal)
                .build();

        String userInput;
        while (true) {
            userInput = lineReader.readLine("You > ");

            if ("exit".equalsIgnoreCase(userInput) || "/quit".equalsIgnoreCase(userInput)) {
                System.out.println("👋 離開聊天模式，返回主畫面...");
                CommandListService.printCommandList();
                break;
            }

            if (userInput.trim().isEmpty()) continue;

            try {
                // String response = chatService.sendMessage(userInput);
                String response = "Mock AI Response";
                System.out.println("AI  > " + response);
            } catch (Exception e) {
                System.out.println("❌ 錯誤: " + e.getMessage());
            }
        }
    }
}
