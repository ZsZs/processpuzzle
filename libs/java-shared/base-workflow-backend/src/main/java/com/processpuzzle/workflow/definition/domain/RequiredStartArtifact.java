package com.processpuzzle.workflow.definition.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One artifact an {@link WorkflowStartConditionType#INPUT_ARTIFACT} start condition waits for, and
 * optionally the state it has to be in.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequiredStartArtifact {

    /** Id of an {@link ArtifactDefinition} of the same organization. */
    private String artifactDefinitionId;

    /**
     * State the artifact has to be in, as named by its base-state machine. Null means any state
     * will do — base-workflow does not resolve the name, base-state does.
     */
    private String state;
}
