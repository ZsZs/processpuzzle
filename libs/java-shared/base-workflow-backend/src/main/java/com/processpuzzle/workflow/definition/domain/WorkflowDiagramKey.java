package com.processpuzzle.workflow.definition.domain;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key of {@link WorkflowDiagram}, identical in shape to {@link WorkflowKey} because
 * a layout is addressed by exactly the same pair: the diagram of the workflow of {@code workflowId},
 * within one organization.
 *
 * <p>A plain mutable class and not a record, for the same reason {@link WorkflowKey} is: JPA requires
 * an {@code @IdClass} to be public, {@link Serializable}, and instantiable through a public no-arg
 * constructor. Field names and types must match the entity's {@code @Id} fields exactly.
 */
public class WorkflowDiagramKey implements Serializable {

    private static final long serialVersionUID = 1L;

    private String orgKey;
    private String workflowId;

    public WorkflowDiagramKey() {
        // required by JPA
    }

    public WorkflowDiagramKey(String orgKey, String workflowId) {
        this.orgKey = orgKey;
        this.workflowId = workflowId;
    }

    public String getOrgKey() {
        return orgKey;
    }

    public void setOrgKey(String orgKey) {
        this.orgKey = orgKey;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof WorkflowDiagramKey that)) {
            return false;
        }
        return Objects.equals(orgKey, that.orgKey) && Objects.equals(workflowId, that.workflowId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orgKey, workflowId);
    }

    @Override
    public String toString() {
        return orgKey + "/" + workflowId;
    }
}
