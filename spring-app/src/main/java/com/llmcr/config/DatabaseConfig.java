package com.llmcr.config;

import java.util.Map;
import java.util.Set;

import com.llmcr.entity.Context.ContextType;;

public class DatabaseConfig {
    public final Map<String, Set<ContextType>> collectionMap = Map.of(
            "PROJECT_CONTEXT", Set.of(ContextType.CLASSNODE, ContextType.DOCUMENT),
            "USECASE", Set.of(ContextType.USECASE),
            "GUIDELINE", Set.of(ContextType.GUIDELINE),
            "TOOLDEF", Set.of(ContextType.TOOLDEF));

}
