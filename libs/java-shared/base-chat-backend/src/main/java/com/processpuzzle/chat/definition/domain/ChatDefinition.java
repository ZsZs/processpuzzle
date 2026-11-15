package com.processpuzzle.chat.definition.domain;

import com.processpuzzle.core.definition.Channel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A designer-authored binding of chat to a kind of subject: which subjects get conversations, how many,
 * when they exist, and which roles read, write and open them.
 *
 * <p>The three role lists name realm roles and are interpreted at access time — nothing about who takes
 * part is copied into a conversation. An empty list means "any member of the organization", narrowed by
 * the list before it: writers must be able to read, openers must be able to read.
 *
 * <p>Like every definition it has a {@link Channel#DRAFT} row a designer edits and a
 * {@link Channel#RELEASED} one the running application reads.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"orgKey", "definitionKey", "channel"})
@ToString
@Entity
@Table(name = "chat_definition")
@IdClass(ChatDefinitionKey.class)
public class ChatDefinition {

    /** Lower-case words joined by hyphens, as the key appears in URLs and seed files. */
    public static final Pattern KEY_PATTERN = Pattern.compile("^[a-z][a-z0-9-]*$");

    @Id
    @Column(name = "org_key", nullable = false)
    private String orgKey;

    /** Chosen by the author, unique per organization. */
    @Id
    @Column(name = "definition_key", nullable = false)
    private String definitionKey;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", length = 16, nullable = false)
    @Builder.Default
    private Channel channel = Channel.DRAFT;

    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubjectType subjectType;

    /** For {@link SubjectType#ENTITY}, the code of the entity whose instances get this chat. */
    @Column(nullable = false)
    private String subjectDefinition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Multiplicity multiplicity = Multiplicity.SINGLE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ChatLifecycle lifecycle = ChatLifecycle.ON_DEMAND;

    /** STATE_BOUND only: the subject states in which the chat accepts messages. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private List<String> writableStates = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private List<String> readRoles = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private List<String> writeRoles = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private List<String> createRoles = new ArrayList<>();

    /** Also admit the subject's owner, where the subject kind reports one. */
    @Column(nullable = false)
    private boolean includeSubjectOwner;

    /** Days after archiving at which a conversation is deleted; null keeps them. Enforced by the retention job. */
    private Integer retentionDays;

    @Version
    private Long version;

    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    // The lists are never null, also for a row stored before the column existed.

    public List<String> getWritableStates() {
        writableStates = writableStates == null ? new ArrayList<>() : writableStates;
        return writableStates;
    }

    public List<String> getReadRoles() {
        readRoles = readRoles == null ? new ArrayList<>() : readRoles;
        return readRoles;
    }

    public List<String> getWriteRoles() {
        writeRoles = writeRoles == null ? new ArrayList<>() : writeRoles;
        return writeRoles;
    }

    public List<String> getCreateRoles() {
        createRoles = createRoles == null ? new ArrayList<>() : createRoles;
        return createRoles;
    }

    /** Every role this definition names, once each — what the organization's role catalog has to contain. */
    public Set<String> allRoles() {
        Set<String> roles = new LinkedHashSet<>();
        roles.addAll(getReadRoles());
        roles.addAll(getWriteRoles());
        roles.addAll(getCreateRoles());
        return roles;
    }

    /** @return the rule violations, empty when the definition is valid */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (definitionKey == null || !KEY_PATTERN.matcher(definitionKey).matches()) {
            errors.add("'key' must be lower-case words joined by hyphens, e.g. 'order-discussion'");
        }
        if (isBlank(name)) {
            errors.add("'name' is required");
        }
        if (subjectType == null) {
            errors.add("'subjectType' is required");
        }
        if (isBlank(subjectDefinition)) {
            errors.add("'subjectDefinition' is required");
        }
        if (multiplicity == null || lifecycle == null) {
            errors.add("'multiplicity' and 'lifecycle' are required");
            return errors;
        }
        validateLifecycle(errors);
        if (retentionDays != null && retentionDays < 1) {
            errors.add("'retentionDays' must be at least 1");
        }
        if (allRoles().stream().anyMatch(ChatDefinition::isBlank) || getWritableStates().stream().anyMatch(ChatDefinition::isBlank)) {
            errors.add("roles and writable states must not be blank");
        }
        return errors;
    }

    private void validateLifecycle(List<String> errors) {
        if (lifecycle == ChatLifecycle.STATE_BOUND && getWritableStates().isEmpty()) {
            errors.add("a STATE_BOUND chat needs at least one writable state");
        }
        if (lifecycle != ChatLifecycle.STATE_BOUND && !getWritableStates().isEmpty()) {
            errors.add("'writableStates' only applies to a STATE_BOUND chat");
        }
        if (lifecycle == ChatLifecycle.AUTO_CREATE && multiplicity == Multiplicity.MANY) {
            errors.add("an AUTO_CREATE chat creates one conversation per subject, so it cannot be MANY");
        }
    }

    /** A new released row with the draft's content: what a definition's first release creates. */
    public static ChatDefinition releasedCopyOf(ChatDefinition draft) {
        ChatDefinition released = ChatDefinition.builder()
                .orgKey(draft.orgKey).definitionKey(draft.definitionKey).channel(Channel.RELEASED).build();
        released.replaceWith(draft);
        return released;
    }

    /** Copies everything an author edits from {@code source}, leaving the key, version and timestamps. */
    public void replaceWith(ChatDefinition source) {
        this.name = source.name;
        this.description = source.description;
        this.subjectType = source.subjectType;
        this.subjectDefinition = source.subjectDefinition;
        this.multiplicity = source.multiplicity;
        this.lifecycle = source.lifecycle;
        this.writableStates = new ArrayList<>(source.getWritableStates());
        this.readRoles = new ArrayList<>(source.getReadRoles());
        this.writeRoles = new ArrayList<>(source.getWriteRoles());
        this.createRoles = new ArrayList<>(source.getCreateRoles());
        this.includeSubjectOwner = source.includeSubjectOwner;
        this.retentionDays = source.retentionDays;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
