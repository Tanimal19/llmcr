package com.llmcr.cli.ui;

import org.jline.keymap.BindingReader;
import org.jline.keymap.KeyMap;
import org.jline.terminal.Terminal;
import org.jline.utils.InfoCmp;
import org.jline.utils.InfoCmp.Capability;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class InteractiveSelector {

    private enum Action {
        UP, DOWN, TOGGLE, TOGGLE_ALL, CONFIRM
    }

    private final Terminal terminal;
    private final List<String> items;
    private final List<Boolean> checked;
    private final boolean isMultiSelect;
    private int currentIndex = 0;

    // 關鍵：用來標記 Ctrl-C 是否被按下的旗標
    private volatile boolean ctrlCPressed = false;

    public InteractiveSelector(Terminal terminal, List<String> items, boolean isMultiSelect) {
        this.terminal = terminal;
        this.items = items;
        this.isMultiSelect = isMultiSelect;
        this.checked = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            this.checked.add(false);
        }
    }

    public List<Integer> select() throws IOException {
        // 先取得目前執行 select 的主執行緒引用
        final Thread mainThread = Thread.currentThread();

        // 設定 Ctrl-C 監聽器：改變旗標，並直接中斷主執行緒，強迫它從 readBinding 的等待中醒來
        Terminal.SignalHandler oldHandler = terminal.handle(Terminal.Signal.INT, signal -> {
            ctrlCPressed = true;
            mainThread.interrupt(); // 喚醒卡在讀取按鍵的主執行緒
        });

        BindingReader bindingReader = new BindingReader(terminal.reader());
        KeyMap<Action> keyMap = new KeyMap<>();

        keyMap.bind(Action.UP, terminal.getStringCapability(Capability.key_up));
        keyMap.bind(Action.DOWN, terminal.getStringCapability(Capability.key_down));
        keyMap.bind(Action.UP, "\033[A", "OA");
        keyMap.bind(Action.DOWN, "\033[B", "OB");
        keyMap.bind(Action.TOGGLE, " ");
        keyMap.bind(Action.TOGGLE_ALL, "A");
        keyMap.bind(Action.CONFIRM, "\r", "\n");

        terminal.enterRawMode();
        terminal.puts(InfoCmp.Capability.cursor_invisible);

        try {
            while (true) {
                // 1. 進入迴圈或重新渲染前，檢查是否被 Ctrl-C
                if (ctrlCPressed || Thread.interrupted()) {
                    cleanLines();
                    return null;
                }

                render();

                Action action = null;
                try {
                    // 如果被中斷，這裡會拋出 java.io.IOError
                    action = bindingReader.readBinding(keyMap);
                } catch (java.io.IOError e) {
                    // 關鍵修正：檢查是不是因為執行緒中斷引起的 IOError
                    if (e.getCause() instanceof java.io.InterruptedIOException || ctrlCPressed) {
                        // 這是我們預期的 Ctrl-C 中斷，優雅地吞掉它，不做任何事
                    } else {
                        throw e; // 如果是其他真正的 IO 錯誤，依然往外丟
                    }
                } catch (Exception e) {
                    // 捕捉其他可能的普通異常
                }

                // 2. 讀取完按鍵後（或是被 Ctrl-C 喚醒後），檢查旗標
                if (ctrlCPressed || Thread.interrupted()) {
                    cleanLines();
                    return null; // 優雅返回 null，回到 Spring Shell 大廳
                }

                if (action == null) {
                    continue;
                }

                switch (action) {
                    case UP:
                        currentIndex = (currentIndex - 1 + items.size()) % items.size();
                        break;
                    case DOWN:
                        currentIndex = (currentIndex + 1) % items.size();
                        break;
                    case TOGGLE:
                        if (isMultiSelect) {
                            checked.set(currentIndex, !checked.get(currentIndex));
                        }
                        break;
                    case TOGGLE_ALL:
                        if (isMultiSelect) {
                            boolean allChecked = !checked.contains(false);
                            for (int i = 0; i < checked.size(); i++) {
                                checked.set(i, !allChecked);
                            }
                        }
                        break;
                    case CONFIRM:
                        return getSelectedIndices();
                }
            }
        } finally {
            terminal.puts(InfoCmp.Capability.cursor_normal);
            if (oldHandler != null) {
                terminal.handle(Terminal.Signal.INT, oldHandler);
            }
            // 清除可能殘留的中斷狀態，避免影響到 Spring Shell 後續的 LineReader
            Thread.interrupted();
            System.out.print("\r");
            System.out.flush();
        }
    }

    private List<Integer> getSelectedIndices() {
        List<Integer> selectedIndices = new ArrayList<>();
        for (int i = 0; i < checked.size(); i++) {
            if (checked.get(i)) selectedIndices.add(i);
        }
        return selectedIndices;
    }

    private void render() {
        cleanLines();
        System.out.println("\nJaveClass/");
        System.out.println("──────────────────────────────────");
        for (int i = 0; i < items.size(); i++) {
            String prefix = (i == currentIndex) ? " > " : "   ";
            String box = "";
            if (isMultiSelect) {
                box = checked.get(i) ? "▣ " : "▢ ";
            }
            System.out.println(prefix + box + items.get(i));
        }
        System.out.println("──────────────────────────────────");
        if (isMultiSelect) {
            System.out.println("space: select/unselect  │  shift+A: all  │  ⇅ scroll  │  Enter: Confirm");
        } else {
            System.out.println("⇅ scroll  │  Enter: Return");
        }
    }

    private void cleanLines() {
        int totalLinesToClear = items.size() + 6;
        for (int i = 0; i < totalLinesToClear; i++) {
            System.out.print("\033[F\033[K");
        }
        System.out.flush();
    }
}
