package com.processpuzzle.workflow.definition.usecases.outbound;

/**
 * The definition layer cannot query the execution layer's {@code WorkflowInstanceRepository}
 * directly from a use case — this port is what {@code DeleteWorkflowUseCase} depends on
 * instead. The implementing adapter (definition/adapters/outbound) is what actually reaches into
 * {@code execution.domain}. Mirrors {@code EntityInstanceExistenceCheckPort} in
 * base-entity-backend exactly.
 */
public interface ActiveWorkflowInstanceExistencePort {

    /**
     * @return true if any workflow instance of this definition is not in a terminal state
     *         (COMPLETED/CANCELLED — see "Only allowed if there are no ACTIVE workflow instances
     *         of this definition" in base-workflow-api.yaml.
     */
    boolean existsActiveInstanceOf(String orgKey, String workflowId);

    /**
     * Backs {@code WorkflowSummary.activeInstances} in the list endpoint.
     */
    long countActiveInstancesOf(String orgKey, String workflowId);
}
