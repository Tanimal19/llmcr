package com.llmcr.service.etl.loader;

import java.util.List;
import java.util.function.Consumer;

import com.llmcr.entity.Context;

public interface ContextLoader extends Consumer<List<Context>> {
}
