package com.llmcr.entity.contextImpl;

import com.llmcr.entity.Source;
import com.llmcr.entity.Context;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "action")
public class ActionContext extends Context {

    @Column(name = "action_key", nullable = false)
    private String actionKey;

    @Column(name = "description", nullable = true, columnDefinition = "TEXT")
    private String description;

    public ActionContext() {
    }

    public ActionContext(Source source, String actionKey, String description) {
        this.setSource(source);
        this.setContextName("Tool::" + actionKey);
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
