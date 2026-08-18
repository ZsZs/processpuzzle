package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.definition.domain.ProcessDefinition;
import com.processpuzzle.workflow.definition.domain.ProcessDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.ProcessDefinitionValidator;
import com.processpuzzle.workflow.definition.domain.TaskDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional
public class ReplaceTaskDefinitionUseCase {

    private final ProcessDefinitionRepository repository;
    private final ProcessDefinitionValidator validator;

    public TaskDefinition replace(String orgKey, String processId, String taskId, TaskDefinition desiredState) {
        ProcessDefinition process = repository.findByOrgKeyAndId(orgKey, processId)
                .orElseThrow(() -> new NotFoundException("No process definition with id '%s'".formatted(processId)));

        TaskDefinition existing = process.findTask(taskId)
                .orElseThrow(() -> new NotFoundException(
                        "No task '%s' in process '%s'".formatted(taskId, processId)));

        existing.setName(desiredState.getName());
        existing.setDescription(desiredState.getDescription());
        existing.setPerformedBy(desiredState.getPerformedBy());
        existing.setInputs(desiredState.getInputs());
        existing.setOutputs(desiredState.getOutputs());
        existing.setPreconditionRuleId(desiredState.getPreconditionRuleId());
        existing.setPostconditionRuleId(desiredState.getPostconditionRuleId());
        existing.setSteps(desiredState.getSteps());
        existing.setDependsOn(desiredState.getDependsOn());
        existing.setParallel(desiredState.isParallel());
        existing.setOverride(desiredState.isOverride());

        validator.validate(process);
        repository.save(process);
        return existing;
    }
}
