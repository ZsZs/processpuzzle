package com.processpuzzle.workflow.execution.usecases.inbound;

import com.processpuzzle.workflow.execution.domain.EventInstance;
import com.processpuzzle.workflow.execution.domain.EventInstanceRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** The intermediate events of workflow instances, as GET instance and the instance list return them. */
@Component
@Transactional(readOnly = true)
public class ListEventInstancesUseCase {

    private final EventInstanceRepository repository;

    public ListEventInstancesUseCase(EventInstanceRepository repository) {
        this.repository = repository;
    }

    public List<EventInstance> findAll(String orgKey, UUID workflowInstanceId) {
        return repository.findByOrgKeyAndWorkflowInstanceId(orgKey, workflowInstanceId);
    }

    /** The events of a page of instances in one query, keyed by instance id. */
    public Map<UUID, List<EventInstance>> findAllByInstance(String orgKey, Collection<UUID> workflowInstanceIds) {
        if (workflowInstanceIds.isEmpty()) {
            return Map.of();
        }
        return repository.findByOrgKeyAndWorkflowInstanceIdIn(orgKey, workflowInstanceIds).stream()
                .collect(Collectors.groupingBy(EventInstance::getWorkflowInstanceId));
    }
}
