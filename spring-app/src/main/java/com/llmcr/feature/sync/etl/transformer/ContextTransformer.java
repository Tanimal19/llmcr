package com.llmcr.feature.sync.etl.transformer;

import com.llmcr.domain.entity.Context;
import java.util.function.Function;

/**
 * ContextTransformer is responsible for transforming a context by modify its field. It can add /
 * remove chunks, or modify the content, type, etc. of the context.
 */
public interface ContextTransformer extends Function<Context, Context> {
  boolean supports(Context context);
}
