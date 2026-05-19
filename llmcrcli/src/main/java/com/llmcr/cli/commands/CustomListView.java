package com.llmcr.cli.commands;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.shell.component.view.control.ListView;

/**
 * 解決 Spring Shell 3.4 ListView 沒有 Getter 的問題
 */
public class CustomListView extends ListView<String> {

    private final List<String> myItems;
    // 用來記錄多選模式下，使用者按下空白鍵切換的索引
    private final Set<Integer> mySelectedIndices = new HashSet<>();
    private int currentActiveIndex = 0;

    public CustomListView(List<String> items, ItemStyle itemStyle) {
        super(items, itemStyle);
        this.myItems = items != null ? items : new ArrayList<>();

        // 預設如果是一開始的 RADIO 模式，預設選中第 0 個
        if (itemStyle == ItemStyle.RADIO && !this.myItems.isEmpty()) {
            mySelectedIndices.add(0);
        }
    }

    // 攔截鍵盤按鍵來同步我們自己的狀態
    // 官方 initInternal 綁定了 1048580 (Enter), 32 (Space), 1048576 (Up), 1048577 (Down)
    @Override
    protected void initInternal() {
        super.initInternal();

        // 重新註冊 Up/Down，順便追蹤當前游標停在第幾個 index
        this.registerKeyBinding(1048576, () -> {
            if (currentActiveIndex > 0) {
                currentActiveIndex--;
            } else {
                currentActiveIndex = myItems.size() - 1; // 循環
            }
        });

        this.registerKeyBinding(1048577, () -> {
            if (currentActiveIndex < myItems.size() - 1) {
                currentActiveIndex++;
            } else {
                currentActiveIndex = 0; // 循環
            }
        });

        // 攔截 Space 鍵 (32)：在多選模式下切換選取狀態
        this.registerKeyBinding(32, () -> {
            if (this.getItemStyle() == ItemStyle.CHECKED) {
                if (mySelectedIndices.contains(currentActiveIndex)) {
                    mySelectedIndices.remove(currentActiveIndex);
                } else {
                    mySelectedIndices.add(currentActiveIndex);
                }
            } else if (this.getItemStyle() == ItemStyle.RADIO) {
                mySelectedIndices.clear();
                mySelectedIndices.add(currentActiveIndex);
            }
        });
    }

    /**
     * 單選模式：獲取目前游標選中的項目
     */
    public String getSelectedItem() {
        if (currentActiveIndex >= 0 && currentActiveIndex < myItems.size()) {
            return myItems.get(currentActiveIndex);
        }
        return null;
    }

    /**
     * 多選模式：獲取所有被打勾的項目
     */
    public List<String> getCheckedItems() {
        List<String> result = new ArrayList<>();
        for (Integer idx : mySelectedIndices) {
            if (idx >= 0 && idx < myItems.size()) {
                result.add(myItems.get(idx));
            }
        }
        return result;
    }
}