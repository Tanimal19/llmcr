package com.llmcr.cli;

import java.util.List;

import org.springframework.stereotype.Component;

import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.CheckBoxList;
import com.googlecode.lanterna.gui2.Direction;
import com.googlecode.lanterna.gui2.LinearLayout;
import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.ProgressBar;
import com.googlecode.lanterna.gui2.TextBox;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;

@Component
public class TuiApplication {

    public void start() throws Exception {

        Terminal terminal = new DefaultTerminalFactory().createTerminal();

        Screen screen = new TerminalScreen(terminal);

        screen.startScreen();

        MultiWindowTextGUI gui = new MultiWindowTextGUI(screen);

        showMainMenu(gui);

        screen.stopScreen();
    }

    private void showMainMenu(MultiWindowTextGUI gui) {

        BasicWindow window = new BasicWindow("Main Menu");

        Panel panel = new Panel();
        panel.setLayoutManager(new LinearLayout(Direction.VERTICAL));

        panel.addComponent(new Button("chat", () -> {
            showChatWindow(gui);
        }));

        panel.addComponent(new Button("review", () -> {
            showReviewWindow(gui);
        }));

        panel.addComponent(new Button("setrag", () -> {
            showSetragWindow(gui);
        }));

        panel.addComponent(new Button("exit", window::close));

        window.setComponent(panel);

        gui.addWindowAndWait(window);
    }

    private void showChatWindow(MultiWindowTextGUI gui) {

        BasicWindow window = new BasicWindow("Chat");

        Panel panel = new Panel();
        panel.setLayoutManager(new LinearLayout(Direction.VERTICAL));

        TextBox output = new TextBox()
                .setReadOnly(true);

        TextBox input = new TextBox();

        Button send = new Button("Send", () -> {

            String msg = input.getText();

            output.addLine("You: " + msg);

            // TODO: call ollama

            output.addLine("AI: hello human");

            input.setText("");
        });

        Button back = new Button("Back", window::close);

        panel.addComponent(output);
        panel.addComponent(input);
        panel.addComponent(send);
        panel.addComponent(back);

        window.setComponent(panel);

        gui.addWindowAndWait(window);
    }

    private void showReviewWindow(MultiWindowTextGUI gui) {

        BasicWindow window = new BasicWindow("Review");

        Panel panel = new Panel();

        ProgressBar progressBar = new ProgressBar();

        panel.addComponent(progressBar);

        window.setComponent(panel);

        gui.addWindow(window);

        new Thread(() -> {

            for (int i = 0; i <= 100; i++) {

                progressBar.setValue(i);

                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            window.close();

        }).start();

        gui.waitForWindowToClose(window);
    }

    private void showSetragWindow(MultiWindowTextGUI gui) {

        BasicWindow window = new BasicWindow("Set RAG");

        Panel panel = new Panel();

        CheckBoxList<String> list = new CheckBoxList<>();

        list.addItem("Document A");
        list.addItem("Document B");
        list.addItem("Document C");

        panel.addComponent(list);

        panel.addComponent(new Button("Confirm", () -> {

            List<String> selected = list.getCheckedItems();

            System.out.println(selected);

            window.close();
        }));

        window.setComponent(panel);

        gui.addWindowAndWait(window);
    }
}