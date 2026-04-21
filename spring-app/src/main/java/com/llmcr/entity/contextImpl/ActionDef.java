package com.llmcr.entity.contextImpl;

import com.llmcr.entity.Source;
import com.llmcr.entity.Context;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "action")
public class ActionDef extends Context {

    @Column(name = "action_name", nullable = false)
    private String actionName;

    @Column(name = "description", nullable = true, columnDefinition = "TEXT")
    private String description;

    public ActionDef() {
    }

    public ActionDef(Source source, String actionName, String description) {
        this.setSource(source);
        this.setName("Act::" + actionName);
        this.setActionName(actionName);
        this.setDescription(description);
    }

    public String getActionName() {
        return actionName;
    }

    public void setActionName(String actionName) {
        this.actionName = actionName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
