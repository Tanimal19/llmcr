package com.llmcr.cli.services;

import java.io.File;

public interface IBackendService {
    // 供 Chat 模式使用的串流或單次對話方法（這裡以單次對話為例）
    String chat(String userInput);

    // 供 Review 模式使用的方法，回傳 Review 結果的 Markdown 內容
    String generateReview(File diffFile) throws Exception;
}