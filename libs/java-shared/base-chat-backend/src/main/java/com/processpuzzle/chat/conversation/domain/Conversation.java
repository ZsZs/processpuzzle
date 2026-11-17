package com.processpuzzle.chat.conversation.domain;

import com.processpuzzle.chat.definition.domain.SubjectType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * One chat on one subject, opened under a chat definition. The subject is referenced by type, definition
 * code and id only; who may take part is worked out from the definition's roles at every access, so nothing
 * about participants is stored here.
 *
 * <p>{@link #lastSeq} numbers the messages. It is advanced only by {@code ConversationRepository#advanceSeq},
 * after {@code lockIfStatus} has taken this row's lock: that serializes the posts of one conversation, so the
 * numbers are gap-free and follow commit order, and a client that has seen {@code n} can ask for everything
 * after {@code n} and miss nothing — which a database sequence cannot promise. The column is not
 * {@code updatable}: a stale copy of this entity saved later must not write an old number back.
 *
 * <p>{@link #bindingKey} is what makes "one conversation per subject" a database fact for a SINGLE
 * definition, and "one topic per title on a subject" for a MANY definition: unique per organization.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString
@Entity
@Table(name = "chat_conversation",
        uniqueConstraints = @UniqueConstraint(name = "uq_chat_conversation_binding", columnNames = {"org_key", "binding_key"}),
        indexes = @Index(name = "idx_chat_conversation_subject", columnList = "org_key, subject_type, subject_id"))
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "org_key", nullable = false, updatable = false)
    private String orgKey;

    @Column(name = "definition_key", nullable = false, updatable = false)
    private String definitionKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false, updatable = false)
    private SubjectType subjectType;

    @Column(name = "subject_definition", nullable = false, updatable = false)
    private String subjectDefinition;

    @Column(name = "subject_id", nullable = false, updatable = false)
    private String subjectId;

    /** The title of a topic; null for the one conversation of a SINGLE definition. */
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ConversationStatus status = ConversationStatus.OPEN;

    /** The {@code seq} of the newest message; 0 while there is none. */
    @Column(name = "last_seq", nullable = false, updatable = false)
    private long lastSeq;

    @Column(name = "binding_key", updatable = false)
    private String bindingKey;

    /** The opener's subject in the identity provider. */
    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    /** The opener's display name as it was when they opened it. */
    @Column(name = "created_by_name", updatable = false)
    private String createdByName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** The key two conversations of a SINGLE definition would collide on: one per subject. */
    public static String bindingKeyOf(String definitionKey, String subjectId) {
        return definitionKey + "|" + subjectId;
    }

    /**
     * The key two topics of a MANY definition would collide on: the same title on the same subject. The title is
     * compared as people read it — without surrounding or repeated blanks and regardless of case — so "Delivery"
     * and " delivery  " are one topic.
     */
    public static String bindingKeyOf(String definitionKey, String subjectId, String title) {
        String normalized = title.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        return bindingKeyOf(definitionKey, subjectId) + "|" + normalized;
    }

    public boolean isOpen() {
        return status == ConversationStatus.OPEN;
    }
}
