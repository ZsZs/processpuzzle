package com.processpuzzle.workflow.execution.domain;

import com.processpuzzle.workflow.definition.domain.ArtifactType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

/**
 * Runtime tracking row for one {@code ArtifactDefinition} within a {@link WorkflowInstance}.
 * {@code currentState} is a cache: the source of truth for state lives in base-state, addressed
 * by {@code stateMachineInstanceId}. This module refreshes the cache when it receives a state
 * change notification (see the execution.adapters.inbound state-change listener) but never writes
 * back to base-state — that direction of the relationship stays owned by base-state's own API.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString
@Entity
@Table(name = "workflow_artifact_instance")
public class ArtifactInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String orgKey;

    @Column(nullable = false)
    private UUID workflowInstanceId;

    @Column(nullable = false)
    private String artifactDefinitionId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ArtifactType type;

    /**
     * The record this artifact is in the run — bound once, from an event's ENTITY_REF parameter or a task naming the
     * artifact's object, and never re-bound (docs/event-parameters.md). Null until then.
     */
    private String entityId;

    /** The bound record's type — a base-entity definition code, or {@code document}. */
    private String entityType;

    private String stateMachineInstanceId;

    private String currentState;

    @Column(nullable = false)
    private Instant updatedAt;
}
