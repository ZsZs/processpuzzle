package com.processpuzzle.document.domain;

import java.io.Serializable;
import java.util.Objects;

/** The {@code @IdClass} for {@link ContentBook}, composite for the reason {@link DocumentKey} gives. */
public class ContentBookKey implements Serializable {

    private static final long serialVersionUID = 1L;

    private String orgKey;
    private String id;

    public ContentBookKey() {
        // required by JPA
    }

    public ContentBookKey(String orgKey, String id) {
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
        if (!(o instanceof ContentBookKey that)) return false;
        return Objects.equals(orgKey, that.orgKey) && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orgKey, id);
    }
}
