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
}
