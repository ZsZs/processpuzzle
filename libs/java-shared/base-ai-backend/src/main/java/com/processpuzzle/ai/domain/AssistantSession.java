package com.processpuzzle.ai.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One design conversation of the assistant: the messages a designer exchanged with it, for the model to read on the
 * next turn. Owned by the designer who opened it, within their organization, and expired after a period without a
 * message. Erased with its organization.
 */
@Entity
@Table(name = "ai_assistant_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssistantSession {

    @Id
    private UUID id;

    @Column(name = "org_key", nullable = false, length = 63)
    private String orgKey;

    @Column(name = "owner_subject", nullable = false, length = 200)
    private String ownerSubject;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("seq ASC")
    private List<AssistantSessionMessage> messages = new ArrayList<>();

    public AssistantSession(String orgKey, String ownerSubject, Instant now) {
        this.id = UUID.randomUUID();
        this.orgKey = orgKey;
        this.ownerSubject = ownerSubject;
        this.createdAt = now;
        this.lastUsedAt = now;
    }

    /** Adds one message at the end of the conversation, and marks the session as used now. */
    public void append(String role, String text, Instant now) {
        messages.add(new AssistantSessionMessage(this, messages.size() + 1, role, text, now));
        lastUsedAt = now;
    }
}
