package com.processpuzzle.workflow.execution.adapters.outbound;

import com.processpuzzle.workflow.definition.usecases.outbound.ActiveProcessInstanceExistencePort;
import com.processpuzzle.workflow.execution.domain.ProcessInstanceRepository;
import com.processpuzzle.workflow.execution.domain.ProcessInstanceStatus;
import org.springframework.stereotype.Component;

import java.util.EnumSet;

/**
 * The concrete implementation of {@code ActiveProcessInstanceExistencePort}, living on the
 * execution side since it is the side that owns {@code ProcessInstanceRepository}. This is the
 * one place the definition and execution layers touch each other, and it does so through a port,
 * not a direct repository dependency — see the port's Javadoc.
 */
@Component
public class ActiveProcessInstanceExistenceAdapter implements ActiveProcessInstanceExistencePort {

    private static final EnumSet<ProcessInstanceStatus> NON_TERMINAL =
            EnumSet.of(ProcessInstanceStatus.ACTIVE, ProcessInstanceStatus.SUSPENDED);

    private final ProcessInstanceRepository repository;

    public ActiveProcessInstanceExistenceAdapter(ProcessInstanceRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean existsActiveInstanceOf(String orgKey, String processDefinitionId) {
        return repository.existsByOrgKeyAndProcessDefinitionIdAndStatusIn(orgKey, processDefinitionId, NON_TERMINAL);
    }

    @Override
    public long countActiveInstancesOf(String orgKey, String processDefinitionId) {
        return repository.countByOrgKeyAndProcessDefinitionIdAndStatusIn(orgKey, processDefinitionId, NON_TERMINAL);
    }
}
