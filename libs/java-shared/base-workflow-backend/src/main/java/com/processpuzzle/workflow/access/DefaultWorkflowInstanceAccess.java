package com.processpuzzle.workflow.access;

import com.processpuzzle.workflow.execution.domain.WorkflowInstanceRepository;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceStatus;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class DefaultWorkflowInstanceAccess implements WorkflowInstanceAccess {

    private final WorkflowInstanceRepository instances;

    DefaultWorkflowInstanceAccess(WorkflowInstanceRepository instances) {
        this.instances = instances;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WorkflowInstanceView> find(String orgKey, String workflowId, UUID instanceId) {
        return instances.findByOrgKeyAndId(orgKey, instanceId)
                .filter(instance -> instance.getWorkflowId().equals(workflowId))
                .map(instance -> new WorkflowInstanceView(instance.getId(), instance.getWorkflowId(), instance.getStatus().name()));
    }

    @Override
    public List<String> statuses() {
        return Arrays.stream(WorkflowInstanceStatus.values()).map(Enum::name).toList();
    }
}
