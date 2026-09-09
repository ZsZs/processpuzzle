package com.processpuzzle.state.domain;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key of {@link StateMachineDefinition}: {@code entityName} is unique only
 * within its organization, mirroring {@code base-entity}'s {@code EntityDefinition.entityName}
 * and, structurally, {@code base-rule}'s {@code RuleDefinitionKey}.
 *
 * <p>Intentionally a plain mutable class and not a record — JPA requires an {@code @IdClass} to
 * be public, {@link Serializable}, and instantiable through a public no-arg constructor, which a
 * record cannot provide.
 */
public class StateMachineDefinitionKey implements Serializable {

    private static final long serialVersionUID = 1L;

    private String orgKey;
    private String entityName;

    public StateMachineDefinitionKey() {
        // required by JPA
    }

    public StateMachineDefinitionKey(String orgKey, String entityName) {
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
        if (!(other instanceof StateMachineDefinitionKey that)) {
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
