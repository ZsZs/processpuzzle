package com.processpuzzle.event.domain;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.shared.event.EventParameterType;
import com.processpuzzle.shared.event.OccurredParameter;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A named event of one organization — {@code OrderCreatedEvent}, {@code OrderConfirmedEvent} — that
 * the rest of the platform refers to by {@link #id}.
 *
 * <p>A SYSTEM definition is bound to a platform fact: {@link #subjectType} (an entity definition
 * code), {@link #action} and, for {@link PlatformEventAction#STATE_CHANGED} only, the {@link #state}
 * entered — absent means any state. {@link #matches(PlatformEvent)} is that binding. MESSAGE and SIGNAL
 * definitions carry no binding; {@link #validate()} holds both rules.
 *
 * <p>{@link #parameters} declare what an occurrence carries (docs/event-parameters.md); {@link #parametersOf} resolves
 * them against one platform fact, {@link #parametersFrom} against what a workflow sent.
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

    /** What an occurrence carries. Never null, also for a row stored before the column existed — see the getter. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private List<EventParameter> parameters = new ArrayList<>();

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

    public List<EventParameter> getParameters() {
        if (parameters == null) {
            parameters = new ArrayList<>();
        }
        return parameters;
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
        validateParameters(errors);
        return errors;
    }

    private void validateParameters(List<String> errors) {
        Set<String> names = new HashSet<>();
        for (EventParameter parameter : getParameters()) {
            String parameterName = parameter.getName();
            if (isBlank(parameterName) || !PARAMETER_NAME.matcher(parameterName).matches()) {
                errors.add("parameter name '%s' is not an identifier".formatted(parameterName));
                continue;
            }
            if (!names.add(parameterName)) {
                errors.add("parameter '%s' is declared twice".formatted(parameterName));
            }
            if (OccurredParameter.SUBJECT.equals(parameterName)) {
                errors.add("parameter name 'subject' is reserved for the record a SYSTEM event is about");
            }
            if (parameter.getType() == null) {
                errors.add("parameter '%s' needs a 'type'".formatted(parameterName));
            } else if (parameter.getType() == EventParameterType.ENTITY_REF && isBlank(parameter.getEntityType())) {
                errors.add("parameter '%s' is an ENTITY_REF and needs an 'entityType'".formatted(parameterName));
            } else if (parameter.getType() != EventParameterType.ENTITY_REF && !isBlank(parameter.getEntityType())) {
                errors.add("parameter '%s' is no ENTITY_REF — leave 'entityType' empty".formatted(parameterName));
            }
        }
    }

    /**
     * The parameters of one platform fact matching this SYSTEM definition: the implicit {@code subject} — the
     * record the fact is about — and every declared one, from the fact's payload attribute of the same name.
     */
    public List<OccurredParameter> parametersOf(PlatformEvent fact) {
        List<OccurredParameter> resolved = new ArrayList<>();
        resolved.add(new OccurredParameter(OccurredParameter.SUBJECT, EventParameterType.ENTITY_REF, fact.subjectType(),
                fact.subjectId()));
        resolved.addAll(parametersFrom(fact.payload()));
        return resolved;
    }

    /** The declared parameters, each from the value of the same name in {@code values} — null when absent. */
    public List<OccurredParameter> parametersFrom(Map<String, Object> values) {
        Map<String, Object> source = values == null ? Map.of() : values;
        return getParameters().stream()
                .filter(parameter -> !isBlank(parameter.getName()))
                .map(parameter -> new OccurredParameter(parameter.getName(), parameter.getType(), parameter.getEntityType(),
                        source.get(parameter.getName())))
                .toList();
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
        this.parameters = source.getParameters().stream()
                .map(parameter -> parameter.toBuilder().build())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static final Pattern PARAMETER_NAME = Pattern.compile("^[A-Za-z_]\\w*$");

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
