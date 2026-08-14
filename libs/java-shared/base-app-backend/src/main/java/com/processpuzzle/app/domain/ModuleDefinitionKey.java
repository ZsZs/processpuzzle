package com.processpuzzle.app.domain;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key of {@link ModuleDefinition}: a module key is unique only within its
 * organization. A plain mutable class rather than a record, for the reason given on
 * {@link AppDefinitionKey}.
 */
public class ModuleDefinitionKey implements Serializable {

    private static final long serialVersionUID = 1L;

    private String orgKey;
    private String key;

    public ModuleDefinitionKey() {
        // required by JPA
    }

    public ModuleDefinitionKey(String orgKey, String key) {
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
        if (!(other instanceof ModuleDefinitionKey that)) {
            return false;
        }
        return Objects.equals(orgKey, that.orgKey) && Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orgKey, key);
    }
}
