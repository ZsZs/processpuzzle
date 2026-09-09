package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.workflow.common.ConflictException;
import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.definition.domain.TaskDefinition;
import com.processpuzzle.workflow.definition.domain.TaskDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Full replace of a task definition. Field-by-field onto the loaded row rather than saving the
 * incoming one, so that {@code orgKey}, {@code id} and the audit columns survive — the same
 * convention {@link ReplaceToolDefinitionUseCase} follows.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class ReplaceTaskDefinitionUseCase {

    private final TaskDefinitionRepository repository;

    public TaskDefinition replace(String orgKey, String id, TaskDefinition desiredState) {
        TaskDefinition existing = repository.findByOrgKeyAndId(orgKey, id)
                .orElseThrow(() -> new NotFoundException("No task definition with id '%s'".formatted(id)));

        if (desiredState.getVersion() != null && !desiredState.getVersion().equals(existing.getVersion())) {
            throw new ConflictException(
                    "Task definition '%s' was modified concurrently — reload and retry".formatted(id));
        }

        existing.setName(desiredState.getName());
        existing.setDescription(desiredState.getDescription());
        existing.setPerformedByRoles(desiredState.getPerformedByRoles());
        existing.setInputs(desiredState.getInputs());
        existing.setOutputs(desiredState.getOutputs());
        existing.setPreconditionRuleId(desiredState.getPreconditionRuleId());
        existing.setPostconditionRuleId(desiredState.getPostconditionRuleId());
        existing.setSteps(desiredState.getSteps());
        return repository.save(existing);
    }
}
