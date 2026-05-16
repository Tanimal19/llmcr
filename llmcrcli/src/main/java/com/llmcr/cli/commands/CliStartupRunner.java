package com.llmcr.cli.commands;

import com.llmcr.cli.services.CommandListService;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

@Component
public class CliStartupRunner {

    public CliStartupRunner() {
    }

    @PostConstruct
    public void init() {
        // 程式啟動完成後顯示一次指令列表
        CommandListService.printCommandList();
    }
}
