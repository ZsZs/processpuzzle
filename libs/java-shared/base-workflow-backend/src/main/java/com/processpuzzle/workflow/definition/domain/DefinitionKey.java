package com.processpuzzle.workflow.definition.domain;

import com.processpuzzle.core.definition.Channel;
import java.io.Serializable;
import java.util.Objects;

/**
 * The composite key of a workflow definition: an id is unique only within its organization and its channel, the draft
 * a designer edits or the row the organization released (see core's {@code docs/definition-release.md}).
 *
 * <p>A plain mutable class and not a record: JPA requires an {@code @IdClass} to have a public no-arg constructor and
 * mutable fields, which a record cannot provide. The field names and types must match the entity's {@code @Id} fields
 * exactly, the {@code channel} of {@link ReleasableDefinition} among them. Two keys are equal only when they are of the
 * same class, so a role and a task of the same id never are.
 */
public abstract class DefinitionKey implements Serializable {

    private static final long serialVersionUID = 1L;

    private String orgKey;
    private String id;
    private Channel channel;

    protected DefinitionKey() {
        // required by JPA
    }

    protected DefinitionKey(String orgKey, String id, Channel channel) {
        this.orgKey = orgKey;
        this.id = id;
        this.channel = channel;
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
        DefinitionKey that = (DefinitionKey) other;
        return Objects.equals(orgKey, that.orgKey) && Objects.equals(id, that.id) && channel == that.channel;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass(), orgKey, id, channel);
    }

    @Override
    public String toString() {
        return orgKey + "/" + id + "/" + channel;
    }
}
