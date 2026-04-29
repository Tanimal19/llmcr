package com.llmcr.service.etl.transformer;

import java.util.List;

import com.llmcr.entity.Context;
import com.llmcr.entity.Chunk;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

/**
 * Split the content of a Context into multiple Chunks
 */
@Component
public class ContextContentSplitter implements ContextSplitter {

    @Override
    public boolean supports(Context context) {
        // only split context that has no chunks yet
        return context.getChunks().isEmpty();
    }

    @Override
    public Context apply(Context context) {
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(400)
                .withMinChunkSizeChars(200)
                .withMinChunkLengthToEmbed(10)
                .build();

        // split the content into chunks
        List<Chunk> contentChunks = splitter.split(new Document(filter(context.getContent()))).stream()
                .map(doc -> new Chunk(doc.getText()))
                .toList();

        // add chunks back to context
        contentChunks.forEach(context::addChunk);

        return context;
    }

    private static String filter(String text) {
        if (text == null) {
            return "";
        }
        return text
                // remove control characters except newlines and tabs
                .replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "")
                // specific cleaning for ANTLR serialized ATN
                .replaceAll("_serializedATN\\s*=\\s*\"[\\s\\S]*?\";",
                        "_serializedATN = \"<ANTLR_SERIALIZED_ATN>\";")
                // collapse runs of spaces/tabs (but not newlines) into a single space
                .replaceAll("[ \\t]+", " ")
                // trim leading/trailing whitespace on each line
                .replaceAll("(?m)^[ \\t]+|[ \\t]+$", "")
                // collapse 3+ consecutive blank lines into 2
                .replaceAll("(\\r?\\n){3,}", "\n\n")
                .strip();
    }
}
