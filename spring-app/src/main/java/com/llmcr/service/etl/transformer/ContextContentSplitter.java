package com.llmcr.service.etl.transformer;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.llmcr.entity.Context;
import com.llmcr.entity.Chunk;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Split the content of a Context into multiple Chunks
 */
@Component
@Order(1) // ensure this transformer runs before others
public class ContextContentSplitter implements ContextTransformer {

    @Override
    public boolean supports(Context context) {
        return true;
    }

    @Override
    public Context apply(Context context) {
        TokenTextSplitter splitter = new TokenTextSplitter();
        AtomicInteger chunkIndex = new AtomicInteger(context.getChunks().size()); // start from existing chunk count

        // split the content into chunks
        List<Chunk> contentChunks = splitter.split(new Document(filter(context.getContent()))).stream()
                .map(doc -> new Chunk(context, chunkIndex.getAndIncrement(), doc.getText()))
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
                // remove control characters except newlines nd tabs
                .replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "")
                // specific cleaning for ANTLR serialized ATN
                .replaceAll("_serializedATN\\s*=\\s*\"[\\s\\S]*?\";",
                        "_serializedATN = \"<ANTLR_SERIALIZED_ATN>\";");
    }
}
