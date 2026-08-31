package com.processpuzzle.workflow.execution.usecases.inbound;

import com.processpuzzle.workflow.common.ConflictException;
import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.common.ValidationException;
import com.processpuzzle.workflow.definition.usecases.inbound.ResolveWorkflowUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.ResolvedWorkflow;
import com.processpuzzle.workflow.execution.domain.WorkflowInstance;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceRepository;
import com.processpuzzle.workflow.execution.domain.TaskInstance;
import com.processpuzzle.workflow.execution.domain.TaskInstanceRepository;
import com.processpuzzle.workflow.execution.domain.TaskInstanceStatus;
import com.processpuzzle.workflow.execution.usecases.outbound.PermitAllRoleMembershipPort;
import com.processpuzzle.workflow.execution.usecases.outbound.RoleMembershipPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Assigns a user to an ACTIVE task. The role is the one this workflow pins for the task
 * ({@code TaskUse.performedBy}), not one of the several the shared task definition
 * merely permits. If that role has an {@code entityRoleId} configured, membership is checked via {@link RoleMembershipPort}
 * (implemented by the host application — see that port's Javadoc) and rejected with a 400 (not
 * 403) per base-workflow-api.yaml's declared response codes for {@code assignTask}.
 *
 * <p>The port is resolved with {@link ObjectProvider#getIfUnique}, not
 * {@code @ConditionalOnMissingBean} — same reasoning as base-app-backend's
 * {@code OrganizationGuard}, which this class's constructor mirrors exactly.
 */
@Component
@Transactional
public class AssignTaskUseCase {

    private final WorkflowInstanceRepository workflowInstanceRepository;
    private final ResolveWorkflowUseCase resolveWorkflow;
    private final TaskInstanceRepository taskInstanceRepository;
    private final RoleMembershipPort roleMembershipPort;

    public AssignTaskUseCase(WorkflowInstanceRepository workflowInstanceRepository,
                              ResolveWorkflowUseCase resolveWorkflow,
                              TaskInstanceRepository taskInstanceRepository,
                              ObjectProvider<RoleMembershipPort> roleMembershipPortProvider) {
        this.workflowInstanceRepository = workflowInstanceRepository;
        this.resolveWorkflow = resolveWorkflow;
        this.taskInstanceRepository = taskInstanceRepository;
        this.roleMembershipPort = roleMembershipPortProvider.getIfUnique(PermitAllRoleMembershipPort::new);
    }

    public TaskInstance assign(String orgKey, UUID workflowInstanceId, String taskDefinitionId, String userId) {
        WorkflowInstance workflowInstance = workflowInstanceRepository.findByOrgKeyAndId(orgKey, workflowInstanceId)
                .orElseThrow(() -> new NotFoundException("No workflow instance with id '%s'".formatted(workflowInstanceId)));

        TaskInstance taskInstance = taskInstanceRepository
                .findByOrgKeyAndWorkflowInstanceIdAndTaskDefinitionId(orgKey, workflowInstanceId, taskDefinitionId)
                .orElseThrow(() -> new NotFoundException(
                        "No task '%s' in workflow instance '%s'".formatted(taskDefinitionId, workflowInstanceId)));

        if (taskInstance.getStatus() != TaskInstanceStatus.ACTIVE) {
            throw new ConflictException("Task '%s' is %s, not ACTIVE — cannot assign".formatted(taskDefinitionId, taskInstance.getStatus()));
        }

        ResolvedWorkflow definition =
                resolveWorkflow.resolveByOrgKeyAndId(orgKey, workflowInstance.getWorkflowId());
        var task = definition.findTask(taskDefinitionId)
                .orElseThrow(() -> new NotFoundException("Task definition '%s' no longer exists".formatted(taskDefinitionId)));
        var role = definition.findRole(task.performedBy()).orElse(null);

        if (role != null && role.getEntityRoleId() != null
                && !roleMembershipPort.isMember(orgKey, userId, role.getEntityRoleId())) {
            throw new ValidationException(
                    "User '%s' does not hold role '%s' required for task '%s'".formatted(userId, role.getId(), taskDefinitionId));
        }

        taskInstance.setAssignedTo(userId);
        return taskInstanceRepository.save(taskInstance);
    }
}
