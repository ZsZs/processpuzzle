package com.processpuzzle.workflow.execution.adapters.inbound;

import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.InstanceDataProbe;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceRepository;
import java.util.Collection;
import org.springframework.stereotype.Component;

/**
 * Tells a Business Starter install how many workflow instances the organization holds, finished ones
 * included: each still names the workflow definition it ran, which the install would replace.
 */
@Component
public class WorkflowInstanceDataProbe implements InstanceDataProbe {

    private final WorkflowInstanceRepository repository;

    public WorkflowInstanceDataProbe(WorkflowInstanceRepository repository) {
        this.repository = repository;
    }

    @Override
    public String label() {
        return "workflow instances";
    }

    @Override
    public long count(String orgKey) {
        return repository.countByOrgKey(orgKey);
    }

    @Override
    public String definitionKind() {
        return DefinitionKinds.WORKFLOW;
    }

    @Override
    public long count(String orgKey, Collection<String> definitionKeys) {
        return definitionKeys.isEmpty() ? 0 : repository.countByOrgKeyAndWorkflowIdIn(orgKey, definitionKeys);
    }
}
