package com.processpuzzle.state.domain;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key of {@link DiagramDefinition}, identical in shape to
 * {@link StateMachineDefinitionKey} because a diagram is addressed by exactly the same pair: the
 * layout of the state machine of {@code entityName}, within one organization.
 *
 * <p>A plain mutable class and not a record, for the same reason
 * {@link StateMachineDefinitionKey} is: JPA requires an {@code @IdClass} to be public,
 * {@link Serializable}, and instantiable through a public no-arg constructor.
 */
public class DiagramDefinitionKey implements Serializable {

    private static final long serialVersionUID = 1L;

    private String orgKey;
    private String entityName;

    public DiagramDefinitionKey() {
        // required by JPA
    }

    public DiagramDefinitionKey(String orgKey, String entityName) {
        this.orgKey = orgKey;
        this.entityName = entityName;
    }

    public String getOrgKey() {
        return orgKey;
    }

    public void setOrgKey(String orgKey) {
        this.orgKey = orgKey;
    }

    public String getEntityName() {
        return entityName;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DiagramDefinitionKey that)) {
            return false;
        }
        return Objects.equals(orgKey, that.orgKey) && Objects.equals(entityName, that.entityName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orgKey, entityName);
    }

    @Override
    public String toString() {
        return orgKey + "/" + entityName;
    }
}
