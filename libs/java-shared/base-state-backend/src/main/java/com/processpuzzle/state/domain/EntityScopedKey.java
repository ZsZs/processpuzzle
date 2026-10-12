package com.processpuzzle.state.domain;

import com.processpuzzle.core.definition.Channel;
import java.io.Serializable;
import java.util.Objects;

/**
 * The primary key every base-state definition shares: the organization, the entity type it is about, and which of
 * its two rows it is — the {@link Channel#DRAFT} the designer edits, or the {@link Channel#RELEASED} one the running
 * application reads. Each definition table has a subclass of its own, so a key of one is never equal to a key of
 * another.
 */
public abstract class EntityScopedKey implements Serializable {

    private static final long serialVersionUID = 1L;

    private String orgKey;
    private String entityName;
    private Channel channel;

    protected EntityScopedKey() {
        // required by JPA
    }

    protected EntityScopedKey(String orgKey, String entityName, Channel channel) {
        this.orgKey = orgKey;
        this.entityName = entityName;
        this.channel = channel;
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

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        EntityScopedKey that = (EntityScopedKey) other;
        return Objects.equals(orgKey, that.orgKey) && Objects.equals(entityName, that.entityName) && channel == that.channel;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass(), orgKey, entityName, channel);
    }

    @Override
    public String toString() {
        return orgKey + "/" + entityName + "/" + channel;
    }
}
