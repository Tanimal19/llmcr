package com.llmcr.infrastructure.rag;

import com.llmcr.domain.entity.Context;

public record ContextScorePair(Context context, float score) {
}