package com.processpuzzle.widget.domain;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key of {@link WidgetDefinition}: a widget key is unique only within its
 * organization, so two tenants may both define {@code cards-grid}.
 *
 * <p>Intentionally a plain mutable class and not a record: JPA requires an {@code @IdClass} to be
 * public, {@link Serializable}, and instantiable through a public no-arg constructor, which a
 * record cannot provide. Field names and types must match the entity's {@code @Id} fields exactly.
 */
public class WidgetDefinitionKey implements Serializable {

    private static final long serialVersionUID = 1L;

    private String orgKey;
    private String key;

    public WidgetDefinitionKey() {
        // required by JPA
    }

    public WidgetDefinitionKey(String orgKey, String key) {
        this.orgKey = orgKey;
        this.key = key;
    }

    public String getOrgKey() {
        return orgKey;
    }

    public void setOrgKey(String orgKey) {
        this.orgKey = orgKey;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof WidgetDefinitionKey that)) {
            return false;
        }
        return Objects.equals(orgKey, that.orgKey) && Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orgKey, key);
    }
}
