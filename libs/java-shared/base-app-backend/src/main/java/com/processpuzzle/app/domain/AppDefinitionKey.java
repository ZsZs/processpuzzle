package com.processpuzzle.app.domain;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key of {@link AppDefinition}: an app definition id is unique only within its
 * organization, so two tenants may both own {@code claims-app}.
 *
 * <p>Intentionally a plain mutable class and not a record: JPA requires an {@code @IdClass} to be
 * public, {@link Serializable}, and instantiable through a public no-arg constructor, which a
 * record cannot provide. Field names and types must match the entity's {@code @Id} fields exactly.
 */
public class AppDefinitionKey implements Serializable {

    private static final long serialVersionUID = 1L;

    private String orgKey;
    private String id;

    public AppDefinitionKey() {
        // required by JPA
    }

    public AppDefinitionKey(String orgKey, String id) {
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
        if (!(other instanceof AppDefinitionKey that)) {
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
