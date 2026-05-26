package com.llmcr.rag;

import com.llmcr.database.entity.Context;

public record ContextScorePair(Context context, float score) {
}