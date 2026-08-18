package com.processpuzzle.workflow.definition.usecases.outbound;

/**
 * The definition layer cannot query the execution layer's {@code ProcessInstanceRepository}
 * directly from a use case — this port is what {@code DeleteProcessDefinitionUseCase} depends on
 * instead. The implementing adapter (definition/adapters/outbound) is what actually reaches into
 * {@code execution.domain}. Mirrors {@code EntityInstanceExistenceCheckPort} in
 * base-entity-backend exactly.
 */
public interface ActiveProcessInstanceExistencePort {

    /**
     * @return true if any process instance of this definition is not in a terminal state
     *         (COMPLETED/CANCELLED — see "Only allowed if there are no ACTIVE process instances
     *         of this definition" in base-workflow-api.yaml.
     */
    boolean existsActiveInstanceOf(String orgKey, String processDefinitionId);

    /**
     * Backs {@code ProcessDefinitionSummary.activeInstances} in the list endpoint.
     */
    long countActiveInstancesOf(String orgKey, String processDefinitionId);
}
