package com.llmcr.cli.commands;

import org.jline.reader.EndOfFileException;
// import com.llmcr.service.ChatService;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

import com.llmcr.cli.configs.CLISignalHandler;
import com.llmcr.cli.services.CommandListService;

import java.io.IOException;

@ShellComponent
public class ChatCommands {

    // @Autowired
    // private ChatService chatService; // 後端：處理 Ollama 對話

    @Autowired
    private CLISignalHandler signalHandler; // 注入

    @ShellMethod(key = "chat", value = "進入與 LM 的互動聊天模式 (輸入 exit 或 /quit 離開)")
    public void chat() {
        System.out.println("\n🤖 進入聊天模式 (輸入 exit /quit 或 Ctrl+C 返回主選單)");
        System.out.println("=".repeat(65));

        try (Terminal terminal = TerminalBuilder.builder()
                .system(true)
                .nativeSignals(true)           // 關鍵：啟用原生 signal
                .build()) {

            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
                    .build();

            while (true) {
                try {
                    String line = reader.readLine("You > ").trim();

                    if (line.isEmpty()) continue;
                    if ("exit".equalsIgnoreCase(line) || "/quit".equalsIgnoreCase(line)) {
                        break;
                    }

                    // String response = chatService.sendMessage(line);
                    String response = "Mock AI Response";
                    System.out.println("AI  > " + response);

                } catch (UserInterruptException e) {
                    // Ctrl + C 被成功捕捉
                    System.out.println("\n👋 已中斷聊天模式 (Ctrl+C)");
                    break;
                } catch (EndOfFileException e) {
                    break;
                } catch (Exception e) {
                    System.out.println("❌ 錯誤: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.out.println("❌ 終端初始化失敗: " + e.getMessage());
        } finally {
            // 無論如何都回到主列表
            CommandListService.printCommandList();
        }
    }
}
