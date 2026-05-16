package com.llmcr.cli;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CliApplication {

    public static void main(String[] args) {
        // 強制進入 interactive mode
        System.setProperty("spring.shell.interactive.enabled", "true");

        // 註冊防護
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n\n👋 程式即將結束...");
        }, "Shutdown-Hook"));

        SpringApplication.run(CliApplication.class, args);
    }
}
