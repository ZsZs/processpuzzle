package com.processpuzzle.chat.conversation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * How far one user has read one conversation — and, by existing, that they <em>follow</em> it.
 *
 * <p>Participation is computed from roles, so there is no list of "my conversations" to count unread
 * messages over. The marker is that list: a user follows a conversation by opening it, posting in it or
 * reading it, and unfollows by deleting the marker. Unread is then plain arithmetic, because message numbers
 * are gap-free: {@code lastSeq - lastReadSeq}. The live stream pushes only followed conversations.
 *
 * <p>The number only moves forward — see {@code ReadMarkerRepository#advance}. Following says nothing about
 * access: every use re-checks the caller's roles, so a marker outlives a role the user has lost without
 * revealing anything.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"conversationId", "userSub"})
@Entity
@Table(name = "chat_read_marker", indexes = @Index(name = "idx_chat_read_marker_user", columnList = "org_key, user_sub"))
@IdClass(ReadMarkerKey.class)
public class ReadMarker {

    @Id
    @Column(name = "conversation_id", nullable = false, updatable = false)
    private UUID conversationId;

    /** The follower's subject in the identity provider. */
    @Id
    @Column(name = "user_sub", nullable = false, updatable = false)
    private String userSub;

    @Column(name = "org_key", nullable = false, updatable = false)
    private String orgKey;

    /** The {@code seq} of the newest message the user has read; 0 when they have read none. */
    @Column(name = "last_read_seq", nullable = false, updatable = false)
    private long lastReadSeq;

    @Column(name = "updated_at", nullable = false, updatable = false)
    private Instant updatedAt;
}
