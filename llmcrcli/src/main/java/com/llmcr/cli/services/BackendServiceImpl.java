package com.llmcr.cli.services;

import org.springframework.stereotype.Service;
import java.io.File;

@Service
public class BackendServiceImpl implements IBackendService {

    @Override
    public String chat(String userInput) {
        // 這裡接實際的後端 SDK
        return "本地端模型對「" + userInput + "」的回覆：這是一個 Mock 回應。";
    }

    @Override
    public String generateReview(File diffFile) throws Exception {
        // 這裡讀取 diffFile 並送給模型
        return "# Motivation\nThe motivation is to add feature B.\n\n" +
               "# Suggestions\nChange this\nChange that\n\n" +
               "# Issues\nIssue description";
    }
}