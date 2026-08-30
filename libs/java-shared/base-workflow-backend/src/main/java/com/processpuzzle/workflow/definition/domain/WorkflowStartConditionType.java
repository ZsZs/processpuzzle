package com.processpuzzle.workflow.definition.domain;

/**
 * The mechanism by which an instance of a {@link Workflow} comes into being. Selects which fields
 * of {@link WorkflowStartCondition} carry meaning.
 *
 * <pre>
 * INPUT_ARTIFACT           SPEM Artifact / Precondition
 * TRIGGERING_EVENT         SPEM Triggering Event / External Task Execution
 * ROLE_DEFINITION          SPEM Role Definition + Task Use (on-demand manual launch)
 * TIME_BASED_PRECONDITION  SPEM Time-based Precondition / Milestone Guard
 * </pre>
 */
public enum WorkflowStartConditionType {
    INPUT_ARTIFACT, TRIGGERING_EVENT, ROLE_DEFINITION, TIME_BASED_PRECONDITION
}
