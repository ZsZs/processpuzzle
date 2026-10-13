package com.processpuzzle.event.domain;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.shared.event.PlatformEvent;
import com.processpuzzle.shared.event.PlatformEventAction;
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
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.ColumnDefault;

/**
 * A named event of one organization — {@code OrderCreatedEvent}, {@code OrderConfirmedEvent} — that
 * the rest of the platform refers to by {@link #id}.
 *
 * <p>A SYSTEM definition is bound to a platform fact: {@link #subjectType} (an entity definition
 * code), {@link #action} and, for {@link PlatformEventAction#STATE_CHANGED} only, the {@link #state}
 * entered — absent means any state. {@link #matches(PlatformEvent)} is that binding. MESSAGE and SIGNAL
 * definitions carry no binding; {@link #validate()} holds both rules.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"orgKey", "id", "channel"})
@ToString
@Entity
@Table(name = "event_definition")
@IdClass(EventDefinitionKey.class)
public class EventDefinition {

    @Id
    @Column(name = "org_key", nullable = false)
    private String orgKey;

    /** Event name, chosen by the author and unique per organization. */
    @Id
    @Column(nullable = false)
    private String id;

    /**
     * Which row of the definition this is: the draft a designer edits, or the one the organization released, which is
     * what the running application matches events against (see core's {@code docs/definition-release.md}).
     * {@code @ColumnDefault} so that {@code ddl-auto} can add the column to a table that already has rows;
     * {@code EventChannelBackfill} then widens the key and gives each row its released copy.
     */
    @Id
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'DRAFT'")
    @Column(name = "channel", length = 16, nullable = false)
    @Builder.Default
    private Channel channel = Channel.DRAFT;

    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventKind kind;

    /** SYSTEM only — the entity definition code the fact is about. */
    private String subjectType;

    /** SYSTEM only. */
    @Enumerated(EnumType.STRING)
    private PlatformEventAction action;

    /** STATE_CHANGED only — the state entered; null matches every state change. */
    private String state;

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

    /** @return the rule violations, empty when the definition is valid */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (isBlank(id)) {
            errors.add("'id' is required");
        }
        if (isBlank(name)) {
            errors.add("'name' is required");
        }
        if (kind == null) {
            errors.add("'kind' is required");
            return errors;
        }
        if (kind == EventKind.SYSTEM) {
            if (isBlank(subjectType)) {
                errors.add("a SYSTEM event needs a 'subjectType'");
            }
            if (action == null) {
                errors.add("a SYSTEM event needs an 'action'");
            }
            if (!isBlank(state) && action != PlatformEventAction.STATE_CHANGED) {
                errors.add("'state' is only meaningful with action STATE_CHANGED");
            }
        } else if (!isBlank(subjectType) || action != null || !isBlank(state)) {
            errors.add("a %s event carries no binding — leave 'subjectType', 'action' and 'state' empty".formatted(kind));
        }
        return errors;
    }

    /** Whether {@code fact} is an occurrence of this definition. Only SYSTEM definitions match anything. */
    public boolean matches(PlatformEvent fact) {
        if (kind != EventKind.SYSTEM || fact == null) {
            return false;
        }
        if (!orgKey.equals(fact.orgKey()) || !subjectType.equals(fact.subjectType()) || action != fact.action()) {
            return false;
        }
        return isBlank(state) || state.equals(fact.state());
    }

    /** A new released row with the draft's content: what a definition's first release creates. */
    public static EventDefinition releasedCopyOf(EventDefinition draft) {
        EventDefinition released = EventDefinition.builder().orgKey(draft.orgKey).id(draft.id).channel(Channel.RELEASED).build();
        released.replaceWith(draft);
        return released;
    }

    /** Copies everything an author edits from {@code source}, leaving the key, version and timestamps. */
    public void replaceWith(EventDefinition source) {
        this.name = source.name;
        this.description = source.description;
        this.kind = source.kind;
        this.subjectType = source.subjectType;
        this.action = source.action;
        this.state = source.state;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
