package com.llmcr.cli.configs;

import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.springframework.context.annotation.Bean;
import org.springframework.shell.jline.PromptProvider;
import org.springframework.shell.standard.ShellComponent;

@ShellComponent
public class ShellConfig {

    @Bean
    public PromptProvider promptProvider() {
        return () -> new AttributedString("> ", AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN));
    }
}
