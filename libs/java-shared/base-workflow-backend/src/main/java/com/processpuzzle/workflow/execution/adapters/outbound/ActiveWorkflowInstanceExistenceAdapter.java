package com.processpuzzle.workflow.execution.adapters.outbound;

import com.processpuzzle.workflow.definition.usecases.outbound.ActiveWorkflowInstanceExistencePort;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceRepository;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceStatus;
import org.springframework.stereotype.Component;

import java.util.EnumSet;

/**
 * The concrete implementation of {@code ActiveWorkflowInstanceExistencePort}, living on the
 * execution side since it is the side that owns {@code WorkflowInstanceRepository}. This is the
 * one place the definition and execution layers touch each other, and it does so through a port,
 * not a direct repository dependency — see the port's Javadoc.
 */
@Component
public class ActiveWorkflowInstanceExistenceAdapter implements ActiveWorkflowInstanceExistencePort {

    private static final EnumSet<WorkflowInstanceStatus> NON_TERMINAL =
            EnumSet.of(WorkflowInstanceStatus.ACTIVE, WorkflowInstanceStatus.SUSPENDED);

    private final WorkflowInstanceRepository repository;

    public ActiveWorkflowInstanceExistenceAdapter(WorkflowInstanceRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean existsActiveInstanceOf(String orgKey, String workflowId) {
        return repository.existsByOrgKeyAndWorkflowIdAndStatusIn(orgKey, workflowId, NON_TERMINAL);
    }

    @Override
    public long countActiveInstancesOf(String orgKey, String workflowId) {
        return repository.countByOrgKeyAndWorkflowIdAndStatusIn(orgKey, workflowId, NON_TERMINAL);
    }
}
