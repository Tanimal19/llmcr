package com.llmcr.cli;

import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.shell.core.command.Command;
import org.springframework.shell.jline.PromptProvider;

import com.llmcr.cli.commands.ChatCmd;

@SpringBootApplication
public class CliApplication {

    public static void main(String[] args) {
        // 強制進入 interactive mode
        System.setProperty("spring.shell.interactive.enabled", "true");
        SpringApplication.run(CliApplication.class, args);
    }

    @Bean
    public PromptProvider PromptProvider() {
        return () -> new AttributedString(">", AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN));
    }

    @Bean
    public Command registerChat() {
        return new ChatCmd();
    }
}
