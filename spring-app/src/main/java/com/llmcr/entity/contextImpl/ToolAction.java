package com.llmcr.entity.contextImpl;

import com.llmcr.entity.Source;
import com.llmcr.entity.Context;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "tool_action")
public class ToolAction extends Context {

    @Column(name = "action_key", nullable = false)
    private String actionKey;

    @Column(name = "description", nullable = true, columnDefinition = "TEXT")
    private String description;

    public ToolAction() {
    }

    public ToolAction(Source source, String actionKey, String description) {
        this.setSource(source);
        this.setContextName("Act::" + actionKey);
        this.setActionKey(actionKey);
        this.setDescription(description);
    }

    public String getActionKey() {
        return actionKey;
    }

    public void setActionKey(String actionKey) {
        this.actionKey = actionKey;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
