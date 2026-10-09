package com.processpuzzle.event.domain;

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
@EqualsAndHashCode(of = {"orgKey", "id"})
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
