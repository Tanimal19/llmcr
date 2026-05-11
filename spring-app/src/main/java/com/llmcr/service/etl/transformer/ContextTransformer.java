package com.llmcr.service.etl.transformer;

import java.util.function.Function;

import com.llmcr.entity.Context;

/**
 * ContextTransformer is responsible for transforming a context by modify its
 * field.
 * It can add / remove chunks, or modify the content, type, etc. of the context.
 */
public interface ContextTransformer extends Function<Context, Context> {
    boolean supports(Context context);
}
