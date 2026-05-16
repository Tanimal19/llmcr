package com.llmcr.cli.configs;

import com.llmcr.cli.services.CommandListService;
import org.springframework.stereotype.Component;
import sun.misc.Signal;
import sun.misc.SignalHandler;

@Component
public class CLISignalHandler implements SignalHandler {

    private volatile boolean isInChatMode = false;
    private volatile boolean isInMainMenu = true;

    public CLISignalHandler() {
        // 註冊 Ctrl+C (SIGINT)
        Signal.handle(new Signal("INT"), this);
    }

    public void setInChatMode(boolean inChatMode) {
        this.isInChatMode = inChatMode;
        this.isInMainMenu = !inChatMode;
    }

    @Override
    public void handle(Signal sig) {
        if (isInChatMode) {
            // 在聊天模式 → 只退出聊天模式
            System.out.println("\n👋 已中斷聊天模式");
            CommandListService.printCommandList();
            isInChatMode = false;
            isInMainMenu = true;
        } else if (isInMainMenu) {
            // 在主畫面 → 真正結束程式
            System.out.println("\n\n👋 感謝使用，再見！");
            System.exit(0);
        } else {
            // 其他情況（review執行中）→ 中斷並回到主畫面
            System.out.println("\n⚠️ 操作已中斷");
            CommandListService.printCommandList();
        }
    }
}