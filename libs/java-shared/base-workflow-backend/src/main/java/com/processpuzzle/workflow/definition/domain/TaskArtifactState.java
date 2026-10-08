package com.processpuzzle.workflow.definition.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The state one task of one workflow expects an artifact in, and the state it leaves it in. The
 * names are the artifact's base-state machine's, recorded as-is like {@link RequiredStartArtifact}'s.
 *
 * <p>Workflow-scoped, so on {@link TaskUse} rather than on the shared {@link TaskDefinition}: what
 * a task reads and writes is the catalog's, which state of it a given workflow moves through is not.
 * Recorded and drawn for now — the engine neither checks {@link #inputState} nor drives the artifact
 * into {@link #outputState} yet.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskArtifactState {

    /** Id of one of the task definition's {@code inputs} or {@code outputs}. */
    private String artifactDefinitionId;

    /** Only for an input; null means any state. */
    private String inputState;

    /** Only for an output; null means unchanged or unstated. */
    private String outputState;
}
