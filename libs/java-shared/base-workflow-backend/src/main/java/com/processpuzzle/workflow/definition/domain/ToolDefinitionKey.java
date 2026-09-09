package com.processpuzzle.workflow.definition.domain;

import java.io.Serializable;
import java.util.Objects;

/** Composite primary key of {@link ToolDefinition}. Same rationale as {@link WorkflowKey}. */
public class ToolDefinitionKey implements Serializable {

    private static final long serialVersionUID = 1L;

    private String orgKey;
    private String id;

    public ToolDefinitionKey() {
        // required by JPA
    }

    public ToolDefinitionKey(String orgKey, String id) {
        this.orgKey = orgKey;
        this.id = id;
    }

    public String getOrgKey() {
        return orgKey;
    }

    public void setOrgKey(String orgKey) {
        this.orgKey = orgKey;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ToolDefinitionKey that)) {
            return false;
        }
        return Objects.equals(orgKey, that.orgKey) && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orgKey, id);
    }

    @Override
    public String toString() {
        return orgKey + "/" + id;
    }
}
