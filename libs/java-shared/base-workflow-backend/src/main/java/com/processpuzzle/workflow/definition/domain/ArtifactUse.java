package com.processpuzzle.workflow.definition.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * An {@link ArtifactDefinition} produced or consumed by one {@link Workflow}. A task of the
 * workflow may only name artifacts the workflow declares this way.
 *
 * <p>See {@link RoleUse} for why a use is an object rather than a bare id.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArtifactUse {

    /** Id of an {@link ArtifactDefinition} of the same organization. */
    private String artifactDefinitionId;

    /**
     * The name this workflow's object of the artifact goes by — UML's {@code new_order : Order}. Once a run binds the
     * artifact to a record, the record's id is the context variable of this name. Null means an anonymous object.
     */
    private String objectName;

    /**
     * Which event parameter binds the artifact: {@code <startEventId|eventUseId>.<parameter>}. Needed only when the
     * workflow declares more than one artifact of one entity type; otherwise any ENTITY_REF parameter of the type binds
     * it (docs/event-parameters.md).
     */
    private String bindFrom;

    /** The event-use or start-event id of {@link #bindFrom}, or null when it names none. */
    public String bindFromSource() {
        int dot = bindFrom == null ? -1 : bindFrom.lastIndexOf('.');
        return dot > 0 ? bindFrom.substring(0, dot) : null;
    }

    /** The parameter name of {@link #bindFrom}, or null when it names none. */
    public String bindFromParameter() {
        int dot = bindFrom == null ? -1 : bindFrom.lastIndexOf('.');
        return dot > 0 && dot < bindFrom.length() - 1 ? bindFrom.substring(dot + 1) : null;
    }
}
