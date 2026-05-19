package com.llmcr.tool;

public class CLIInteractable implements InteractionTool.Interactable {
    @Override
    public String askFollowUp(String question) {
        System.out.println("[Retrieval Agent] ask you a question: " + question);
        System.out.print("Your answer: ");
        return System.console().readLine();
    }
}