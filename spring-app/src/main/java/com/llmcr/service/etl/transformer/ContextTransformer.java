package com.llmcr.service.etl.transformer;

import java.util.function.Function;

import com.llmcr.entity.Context;

public interface ContextTransformer extends Function<Context, Context> {
    boolean supports(Context context);
}
