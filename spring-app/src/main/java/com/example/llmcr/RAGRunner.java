package com.example.llmcr;

import java.util.Map;
import java.util.Scanner;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.llmcr.service.faiss.FaissVectorStore;
import com.example.llmcr.service.faiss.FaissVectorStoreFactory;
import com.example.llmcr.service.rag.RAGService;
import com.example.llmcr.service.rag.augmentation.AnswerQueryTemplate;
import com.example.llmcr.service.rag.retrieval.AdaptiveKStrategy;
import com.example.llmcr.service.rag.retrieval.fusion.RankFusionStrategy;

@Component
@ConditionalOnProperty(name = "app.mode", havingValue = "rag")
public class RAGRunner implements CommandLineRunner {
    @Autowired
    private ChatModel defaultChatModel;

    @Autowired
    private FaissVectorStoreFactory FaissVectorStoreFactory;

    @Override
    public void run(String... args) throws Exception {
        FaissVectorStore defaultFaiss = FaissVectorStoreFactory.create("enriched");
        RAGService r = new RAGService(defaultChatModel, defaultFaiss);
        r.setRetrievalStrategy(new AdaptiveKStrategy());
        r.setFusionStrategy(new RankFusionStrategy());
        r.setRAGTemplate(new AnswerQueryTemplate());
        r.setTopK(10);

        // get query from console
        boolean isExit = false;
        Scanner scanner = new Scanner(System.in);
        while (!isExit) {
            System.out.print("Enter your query (or 'exit' to quit): ");
            String query = scanner.nextLine();
            System.out.println(query);
            if (query.equalsIgnoreCase("exit")) {
                isExit = true;
                scanner.close();
                System.out.println("Exiting...");
                break;
            }

            Map<String, Object> reponse = r.generation(query);
            System.out.println("Response: " + reponse.get("response"));
        }
    }
}
