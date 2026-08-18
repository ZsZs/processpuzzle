package com.processpuzzle.workflow.execution.usecases.inbound;

import com.processpuzzle.workflow.common.ConflictException;
import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.common.ValidationException;
import com.processpuzzle.workflow.definition.domain.ProcessDefinition;
import com.processpuzzle.workflow.definition.domain.ProcessDefinitionRepository;
import com.processpuzzle.workflow.execution.domain.ProcessInstance;
import com.processpuzzle.workflow.execution.domain.ProcessInstanceRepository;
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
 * Assigns a user to an ACTIVE task. If the task's role ({@code TaskDefinition.performedBy}) has
 * an {@code entityRoleId} configured, membership is checked via {@link RoleMembershipPort}
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

    private final ProcessInstanceRepository processInstanceRepository;
    private final ProcessDefinitionRepository processDefinitionRepository;
    private final TaskInstanceRepository taskInstanceRepository;
    private final RoleMembershipPort roleMembershipPort;

    public AssignTaskUseCase(ProcessInstanceRepository processInstanceRepository,
                              ProcessDefinitionRepository processDefinitionRepository,
                              TaskInstanceRepository taskInstanceRepository,
                              ObjectProvider<RoleMembershipPort> roleMembershipPortProvider) {
        this.processInstanceRepository = processInstanceRepository;
        this.processDefinitionRepository = processDefinitionRepository;
        this.taskInstanceRepository = taskInstanceRepository;
        this.roleMembershipPort = roleMembershipPortProvider.getIfUnique(PermitAllRoleMembershipPort::new);
    }

    public TaskInstance assign(String orgKey, UUID processInstanceId, String taskDefinitionId, String userId) {
        ProcessInstance processInstance = processInstanceRepository.findByOrgKeyAndId(orgKey, processInstanceId)
                .orElseThrow(() -> new NotFoundException("No process instance with id '%s'".formatted(processInstanceId)));

        TaskInstance taskInstance = taskInstanceRepository
                .findByOrgKeyAndProcessInstanceIdAndTaskDefinitionId(orgKey, processInstanceId, taskDefinitionId)
                .orElseThrow(() -> new NotFoundException(
                        "No task '%s' in process instance '%s'".formatted(taskDefinitionId, processInstanceId)));

        if (taskInstance.getStatus() != TaskInstanceStatus.ACTIVE) {
            throw new ConflictException("Task '%s' is %s, not ACTIVE — cannot assign".formatted(taskDefinitionId, taskInstance.getStatus()));
        }

        ProcessDefinition definition = processDefinitionRepository
                .findByOrgKeyAndId(orgKey, processInstance.getProcessDefinitionId())
                .orElseThrow(() -> new NotFoundException("Process definition no longer exists"));
        var taskDef = definition.findTask(taskDefinitionId)
                .orElseThrow(() -> new NotFoundException("Task definition '%s' no longer exists".formatted(taskDefinitionId)));
        var role = definition.findRole(taskDef.getPerformedBy()).orElse(null);

        if (role != null && role.getEntityRoleId() != null
                && !roleMembershipPort.isMember(orgKey, userId, role.getEntityRoleId())) {
            throw new ValidationException(
                    "User '%s' does not hold role '%s' required for task '%s'".formatted(userId, role.getId(), taskDefinitionId));
        }

        taskInstance.setAssignedTo(userId);
        return taskInstanceRepository.save(taskInstance);
    }
}
