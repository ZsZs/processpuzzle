package com.processpuzzle.artifact.domain;

import java.io.Serializable;
import java.util.Objects;

/**
 * The {@code @IdClass} for {@link Artifact}: an artifact id is unique only within its
 * organization, exactly as {@code RuleDefinitionKey} and {@code AppDefinitionKey} establish
 * for their own modules. JPA requires this class to have a public no-arg constructor,
 * {@code equals}/{@code hashCode} over the same fields as the {@code @Id} fields, and to
 * implement {@link Serializable}.
 */
public class ArtifactKey implements Serializable {

    private static final long serialVersionUID = 1L;

    private String orgKey;
    private String id;

    public ArtifactKey() {
        // required by JPA
    }

    public ArtifactKey(String orgKey, String id) {
        this.orgKey = orgKey;
        this.id = id;
    }

    public String getOrgKey() {
        return orgKey;
    }

    public String getId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ArtifactKey that)) return false;
        return Objects.equals(orgKey, that.orgKey) && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orgKey, id);
    }
}
