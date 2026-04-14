package com.llmcr.service.rag.augmentation;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.template.st.StTemplateRenderer;

public abstract class RAGTemplate {

    public abstract List<String> getQueries(Object input);

    public abstract PromptBuilder getBuilder();

    public abstract class PromptBuilder {

        protected boolean isInputAugmented = false;
        protected boolean isContextAugmented = false;

        protected PromptBuilder() {
        }

        protected abstract String getTemplate();

        protected abstract Map<String, Object> getVariables();

        public final PromptBuilder augmentInput(Object input) {
            doAugmentInput(input);
            this.isInputAugmented = true;
            return this;
        }

        public final PromptBuilder augmentContext(List<Document> documents) {
            assert this.isInputAugmented
                    : "Input must be augmented before context augmentation.";

            doAugmentContext(documents);
            this.isContextAugmented = true;
            return this;
        }

        public final Prompt build() {
            assert this.isInputAugmented
                    : "Input must be augmented before building the prompt.";
            assert this.isContextAugmented
                    : "Context must be augmented before building the prompt.";

            return PromptTemplate.builder()
                    .renderer(StTemplateRenderer.builder()
                            .startDelimiterToken('<')
                            .endDelimiterToken('>')
                            .build())
                    .template(this.getTemplate())
                    .build()
                    .create(this.getVariables());
        }

        protected abstract void doAugmentInput(Object input);

        protected abstract void doAugmentContext(List<Document> documents);
    }
}
