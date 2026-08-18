package com.processpuzzle.workflow.execution.usecases.inbound;

import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.definition.domain.ProcessDefinition;
import com.processpuzzle.workflow.definition.domain.ProcessDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.WorkProductDefinition;
import com.processpuzzle.workflow.execution.domain.ProcessInstance;
import com.processpuzzle.workflow.execution.domain.ProcessInstanceRepository;
import com.processpuzzle.workflow.execution.domain.ProcessInstanceStatus;
import com.processpuzzle.workflow.execution.domain.TaskInstance;
import com.processpuzzle.workflow.execution.domain.TaskInstanceRepository;
import com.processpuzzle.workflow.execution.domain.TaskInstanceStatus;
import com.processpuzzle.workflow.execution.domain.WorkProductInstance;
import com.processpuzzle.workflow.execution.domain.WorkProductInstanceRepository;
import com.processpuzzle.workflow.execution.events.ProcessInstanceStartedEvent;
import com.processpuzzle.workflow.execution.events.WorkProductInstanceCreatedEvent;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Starts a new instance of a process definition: creates the {@link ProcessInstance}, one
 * {@link TaskInstance} per {@code TaskDefinition} (all initially PENDING), one
 * {@link WorkProductInstance} per {@code WorkProductDefinition}, then hands off to
 * {@link TaskActivationService} to activate whichever tasks are immediately eligible (those with
 * an empty {@code dependsOn}).
 */
@Component
@Transactional
public class StartProcessInstanceUseCase {

    private final ProcessDefinitionRepository processDefinitionRepository;
    private final ProcessInstanceRepository processInstanceRepository;
    private final TaskInstanceRepository taskInstanceRepository;
    private final WorkProductInstanceRepository workProductInstanceRepository;
    private final TaskActivationService taskActivationService;
    private final ApplicationEventPublisher eventPublisher;

    public StartProcessInstanceUseCase(ProcessDefinitionRepository processDefinitionRepository,
                                        ProcessInstanceRepository processInstanceRepository,
                                        TaskInstanceRepository taskInstanceRepository,
                                        WorkProductInstanceRepository workProductInstanceRepository,
                                        TaskActivationService taskActivationService,
                                        ApplicationEventPublisher eventPublisher) {
        this.processDefinitionRepository = processDefinitionRepository;
        this.processInstanceRepository = processInstanceRepository;
        this.taskInstanceRepository = taskInstanceRepository;
        this.workProductInstanceRepository = workProductInstanceRepository;
        this.taskActivationService = taskActivationService;
        this.eventPublisher = eventPublisher;
    }

    public ProcessInstance start(String orgKey, String processDefinitionId, String entityId, Map<String, Object> initialContext) {
        ProcessDefinition definition = processDefinitionRepository.findByOrgKeyAndId(orgKey, processDefinitionId)
                .orElseThrow(() -> new NotFoundException("No process definition with id '%s'".formatted(processDefinitionId)));

        ProcessInstance instance = processInstanceRepository.save(ProcessInstance.builder()
                .orgKey(orgKey)
                .processDefinitionId(definition.getId())
                .processDefinitionName(definition.getName())
                .status(ProcessInstanceStatus.ACTIVE)
                .entityId(entityId)
                .context(initialContext == null ? new HashMap<>() : new HashMap<>(initialContext))
                .startedAt(Instant.now())
                .build());

        for (WorkProductDefinition wpDef : definition.getWorkProducts()) {
            WorkProductInstance wpInstance = WorkProductInstance.builder()
                    .orgKey(orgKey)
                    .processInstanceId(instance.getId())
                    .workProductDefinitionId(wpDef.getId())
                    .name(wpDef.getName())
                    .type(wpDef.getType())
                    .updatedAt(Instant.now())
                    .build();
            wpInstance = workProductInstanceRepository.save(wpInstance);
            eventPublisher.publishEvent(new WorkProductInstanceCreatedEvent(
                    orgKey, instance.getId(), wpInstance.getId(), wpDef.getId(), wpDef.getStateMachineId(), entityId));
        }

        definition.getTasks().forEach(taskDef -> taskInstanceRepository.save(TaskInstance.builder()
                .orgKey(orgKey)
                .processInstanceId(instance.getId())
                .taskDefinitionId(taskDef.getId())
                .name(taskDef.getName())
                .status(TaskInstanceStatus.PENDING)
                .build()));

        taskActivationService.activateEligibleTasks(orgKey, definition, instance.getId(), instance.getContext());
        eventPublisher.publishEvent(new ProcessInstanceStartedEvent(orgKey, instance.getId(), definition.getId(), entityId));

        return instance;
    }
}
