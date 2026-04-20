package com.llmcr.action;

import java.util.Map;

public abstract class Action {
    public Object execute(Map<String, Object> parameters);

    public String getDescription();

    public String getDefinition();
}
