package com.processpuzzle.basestate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Persisted state machine definition, identified by ({@code orgKey}, {@code entityName}) — see
 * {@link StateMachineDefinitionKey}. Exactly one per {@code entityName} within an organization
 * (1:1 with the entity type it governs), enforced by {@code CreateStateMachineDefinition} rather
 * than at the database level, the same way {@code base-rule} enforces its own uniqueness rules in
 * the service layer.
 *
 * <p>{@code states} and {@code transitions} are stored as a serialized JSON blob rather than as
 * their own tables: neither has identity or a lifecycle independent of the state machine that
 * declares them — see {@link State} and {@link Transition} — and a whole-document replace ({@code
 * UpdateStateMachineDefinition}) is the only way either ever changes. This mirrors {@code
 * RuleDefinition.fields}' element-collection choice in spirit, but a converter is used here
 * instead of {@code @ElementCollection} because a state carries several fields and nested guard /
 * action lists, which {@code @ElementCollection} cannot express without its own join tables.
 *
 * <p>Deliberately a portable {@code @Lob} text column, not a Postgres-specific
 * {@code columnDefinition = "jsonb"}: unlike {@code EntityObject}'s payload in base-entity, which
 * needs {@code jsonb_path_exists} for RSQL filtering, nothing here ever queries into this JSON —
 * it is always read and written whole. A genuine {@code jsonb} column would also fail schema
 * creation against H2 (used for local dev/test — see {@code application.yaml}'s
 * {@code ddl-auto: update}), which recognizes {@code JSON} but not {@code JSONB} as a type name.
 */
@Entity
@Table(name = "state_machine_definitions")
@IdClass(StateMachineDefinitionKey.class)
public class StateMachineDefinition {

    @Id
    @Column(name = "org_key", length = 63)
    private String orgKey;

    @Id
    @Column(name = "entity_name", length = 100)
    private String entityName;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(name = "state_attribute_key", nullable = false, length = 100)
    private String stateAttributeKey;

    @Column(name = "initial_state_key", nullable = false, length = 100)
    private String initialStateKey;

    @Lob
    @Convert(converter = StatesConverter.class)
    @Column(nullable = false)
    private List<State> states = new ArrayList<>();

    @Lob
    @Convert(converter = TransitionsConverter.class)
    @Column(nullable = false)
    private List<Transition> transitions = new ArrayList<>();

    @Version
    private Long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected StateMachineDefinition() {
        // required by JPA
    }

    private StateMachineDefinition(Builder builder) {
        this.orgKey = builder.orgKey;
        this.entityName = builder.entityName;
        this.name = builder.name;
        this.description = builder.description;
        this.stateAttributeKey = builder.stateAttributeKey;
        this.initialStateKey = builder.initialStateKey;
        this.states = builder.states == null ? new ArrayList<>() : new ArrayList<>(builder.states);
        this.transitions = builder.transitions == null ? new ArrayList<>() : new ArrayList<>(builder.transitions);
    }

    public static Builder builder() {
        return new Builder();
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /** The owning organization. Part of the identity, so there is deliberately no setter. */
    public String getOrgKey() {
        return orgKey;
    }

    /** The entity type this state machine governs. Part of the identity, so no setter. */
    public String getEntityName() {
        return entityName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStateAttributeKey() {
        return stateAttributeKey;
    }

    public void setStateAttributeKey(String stateAttributeKey) {
        this.stateAttributeKey = stateAttributeKey;
    }

    public String getInitialStateKey() {
        return initialStateKey;
    }

    public void setInitialStateKey(String initialStateKey) {
        this.initialStateKey = initialStateKey;
    }

    public List<State> getStates() {
        return List.copyOf(states);
    }

    public List<Transition> getTransitions() {
        return List.copyOf(transitions);
    }

    /** Whole-document replace of the topology — see {@code UpdateStateMachineDefinition}. */
    public void replaceTopology(String name, String description, String stateAttributeKey,
                                 String initialStateKey, List<State> states, List<Transition> transitions) {
        this.name = name;
        this.description = description;
        this.stateAttributeKey = stateAttributeKey;
        this.initialStateKey = initialStateKey;
        this.states = states == null ? new ArrayList<>() : new ArrayList<>(states);
        this.transitions = transitions == null ? new ArrayList<>() : new ArrayList<>(transitions);
    }

    /** The declared {@link State} matching {@code key}, if any. */
    public java.util.Optional<State> findState(String key) {
        return states.stream().filter(s -> s.key().equals(key)).findFirst();
    }

    /**
     * Every {@link Transition} whose {@code sourceStateKey} is {@code currentStateKey} — the
     * candidate set {@code StateMachineEngine} dry-runs or fires against.
     */
    public List<Transition> transitionsFrom(String currentStateKey) {
        return transitions.stream().filter(t -> t.sourceStateKey().equals(currentStateKey)).toList();
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public static class Builder {
        private String orgKey;
        private String entityName;
        private String name;
        private String description;
        private String stateAttributeKey;
        private String initialStateKey;
        private List<State> states = new ArrayList<>();
        private List<Transition> transitions = new ArrayList<>();

        public Builder orgKey(String orgKey) {
            this.orgKey = orgKey;
            return this;
        }

        public Builder entityName(String entityName) {
            this.entityName = entityName;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder stateAttributeKey(String stateAttributeKey) {
            this.stateAttributeKey = stateAttributeKey;
            return this;
        }

        public Builder initialStateKey(String initialStateKey) {
            this.initialStateKey = initialStateKey;
            return this;
        }

        public Builder states(List<State> states) {
            this.states = states;
            return this;
        }

        public Builder transitions(List<Transition> transitions) {
            this.transitions = transitions;
            return this;
        }

        public StateMachineDefinition build() {
            return new StateMachineDefinition(this);
        }
    }
}
