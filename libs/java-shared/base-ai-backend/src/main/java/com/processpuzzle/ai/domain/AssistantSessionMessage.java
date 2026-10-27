package com.processpuzzle.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One message of an assistant session, in order: the designer's ({@code user}) or the assistant's ({@code assistant}). */
@Entity
@Table(name = "ai_assistant_session_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssistantSessionMessage {

    /** The longest text kept for one message; the designer's text is limited to this as well. */
    public static final int MAX_TEXT_LENGTH = 8000;

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private AssistantSession session;

    @Column(nullable = false)
    private int seq;

    @Column(nullable = false, length = 16)
    private String role;

    @Column(nullable = false, length = MAX_TEXT_LENGTH)
    private String text;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    AssistantSessionMessage(AssistantSession session, int seq, String role, String text, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.session = session;
        this.seq = seq;
        this.role = role;
        this.text = text;
        this.createdAt = createdAt;
    }
}
