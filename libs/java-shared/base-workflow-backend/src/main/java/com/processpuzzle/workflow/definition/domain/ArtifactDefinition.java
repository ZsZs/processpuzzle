package com.processpuzzle.workflow.definition.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * SPEM's work product, under the name the platform uses for it: everything a task reads or writes
 * is one of these, because an artifact is also where the lifecycle lives — {@link #artifactType}
 * says what kind of thing it is and {@link #stateMachineId} which base-state machine governs it.
 *
 * <p>A catalog entity like {@link RoleDefinition}: the same artifact is produced or consumed by
 * tasks of several workflows, so it is defined once per organization and referenced from every
 * {@link Workflow#getArtifacts()} that involves it, through an {@link ArtifactUse}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false, of = {"orgKey", "id"})
@ToString
@Entity
@Table(name = "workflow_artifact_definition")
@IdClass(ArtifactDefinitionKey.class)
public class ArtifactDefinition extends com.processpuzzle.workflow.common.Auditable {

    @Id
    @Column(name = "org_key", nullable = false)
    private String orgKey;

    /** Artifact code, chosen by the author and unique per organization. */
    @Id
    @Column(nullable = false)
    private String id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ArtifactType artifactType;

    /**
     * Id of the concrete thing this artifact is: a base-entity entity name when
     * {@link #artifactType} is {@link ArtifactType#ENTITY}, a base-document document name when
     * {@link ArtifactType#DOCUMENT}, or a widget name when {@link ArtifactType#WIDGET}.
     */
    private String artifactTypeId;

    /**
     * ID of the state machine definition in base-state that governs this artifact's lifecycle.
     * Passed when publishing {@code ArtifactInstanceCreatedEvent} so base-state can initialize
     * the right machine.
     */
    private String stateMachineId;

    @Version
    private Long version;
}
