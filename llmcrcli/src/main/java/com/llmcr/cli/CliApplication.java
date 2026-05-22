package com.llmcr.cli;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import com.googlecode.lanterna.gui2.ActionListBox;
import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;

@SpringBootApplication
public class CliApplication {

    public static void main(String[] args) throws Exception {

        ConfigurableApplicationContext ctx =
                SpringApplication.run(
                        CliApplication.class,
                        args
                );

        Terminal terminal =
                new DefaultTerminalFactory()
                        .createTerminalEmulator();

        Screen screen =
                new TerminalScreen(terminal);

        screen.startScreen();

        MultiWindowTextGUI gui =
                new MultiWindowTextGUI(screen);

        BasicWindow window =
                new BasicWindow("Main Menu");

        Panel panel = new Panel();

        ActionListBox menu =
                new ActionListBox();

        menu.addItem("chat", () -> {
            System.out.println("chat");
        });

        menu.addItem("review", () -> {
            System.out.println("review");
        });

        menu.addItem("setrag", () -> {
            System.out.println("setrag");
        });

        menu.addItem("exit", window::close);

        panel.addComponent(menu);

        window.setComponent(panel);

        gui.addWindowAndWait(window);

        screen.stopScreen();

        ctx.close();
    }
}