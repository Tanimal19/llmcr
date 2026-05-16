package com.llmcr.cli.services;

public class CommandListService {

    public static void printCommandList() {
        System.out.println("\n" + "=".repeat(65));
        System.out.println("                    📋  主指令列表");
        System.out.println("=".repeat(65));
        System.out.println("  chat                          → 進入 LLM 聊天模式");
        System.out.println("  review <diff_filepath>        → 執行 Code Review");
        System.out.println("  help                          → 顯示所有指令說明");
        System.out.println("  exit | quit                   → 結束程式");
        System.out.println("=".repeat(65));
    }
}
