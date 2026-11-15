package com.processpuzzle.chat.definition.domain;

import com.processpuzzle.core.definition.Channel;
import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key of {@link ChatDefinition}: a definition's key is unique only within its
 * organization, and each has a draft and a released row. A plain mutable class rather than a record,
 * because an {@code @IdClass} needs a public no-arg constructor.
 */
public class ChatDefinitionKey implements Serializable {

    private static final long serialVersionUID = 1L;

    private String orgKey;
    private String definitionKey;
    private Channel channel;

    public ChatDefinitionKey() {
        // required by JPA
    }

    public ChatDefinitionKey(String orgKey, String definitionKey, Channel channel) {
        this.orgKey = orgKey;
        this.definitionKey = definitionKey;
        this.channel = channel;
    }

    public String getOrgKey() {
        return orgKey;
    }

    public void setOrgKey(String orgKey) {
        this.orgKey = orgKey;
    }

    public String getDefinitionKey() {
        return definitionKey;
    }

    public void setDefinitionKey(String definitionKey) {
        this.definitionKey = definitionKey;
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
        if (!(other instanceof ChatDefinitionKey that)) {
            return false;
        }
        return Objects.equals(orgKey, that.orgKey) && Objects.equals(definitionKey, that.definitionKey) && channel == that.channel;
    }

    @Override
    public int hashCode() {
        return Objects.hash(orgKey, definitionKey, channel);
    }

    @Override
    public String toString() {
        return orgKey + "/" + definitionKey + "/" + channel;
    }
}
