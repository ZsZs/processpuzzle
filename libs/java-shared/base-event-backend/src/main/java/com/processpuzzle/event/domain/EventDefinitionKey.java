package com.processpuzzle.event.domain;

import com.processpuzzle.core.definition.Channel;
import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key of {@link EventDefinition}: an event's {@code id} is unique only within its
 * organization. A plain mutable class rather than a record, because JPA needs an {@code @IdClass} to
 * have a public no-arg constructor.
 */
public class EventDefinitionKey implements Serializable {

    private static final long serialVersionUID = 1L;

    private String orgKey;
    private String id;
    private Channel channel;

    public EventDefinitionKey() {
        // required by JPA
    }

    public EventDefinitionKey(String orgKey, String id, Channel channel) {
        this.orgKey = orgKey;
        this.id = id;
        this.channel = channel;
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
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

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EventDefinitionKey that)) {
            return false;
        }
        return Objects.equals(orgKey, that.orgKey) && Objects.equals(id, that.id) && channel == that.channel;
    }

    @Override
    public int hashCode() {
        return Objects.hash(orgKey, id, channel);
    }

    @Override
    public String toString() {
        return orgKey + "/" + id + "/" + channel;
    }
}
