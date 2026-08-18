package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.workflow.common.ConflictException;
import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.definition.domain.ProcessDefinition;
import com.processpuzzle.workflow.definition.domain.ProcessDefinitionExtendsValidator;
import com.processpuzzle.workflow.definition.domain.ProcessDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.ProcessDefinitionValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Full replace of a process definition's content. {@code desiredState.getVersion()} must match
 * the stored version for JPA optimistic locking to reject lost updates — the caller is expected
 * to have read the current version via GET first, per "Use version to prevent lost updates" in
 * base-workflow-api.yaml.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class ReplaceProcessDefinitionUseCase {

    private final ProcessDefinitionRepository repository;
    private final ProcessDefinitionValidator validator;
    private final ProcessDefinitionExtendsValidator extendsValidator;

    public ProcessDefinition replace(String orgKey, String id, ProcessDefinition desiredState) {
        ProcessDefinition existing = repository.findByOrgKeyAndId(orgKey, id)
                .orElseThrow(() -> new NotFoundException("No process definition with id '%s'".formatted(id)));

        if (!id.equals(desiredState.getId())) {
            throw new ConflictException("id is immutable — cannot rename '%s' to '%s'".formatted(id, desiredState.getId()));
        }
        if (desiredState.getVersion() != null && !desiredState.getVersion().equals(existing.getVersion())) {
            throw new ConflictException(
                    "Process definition '%s' was modified concurrently — reload and retry".formatted(id));
        }

        extendsValidator.validate(orgKey, id, desiredState.getExtendsProcessId());

        existing.replaceContent(
                desiredState.getName(),
                desiredState.getDescription(),
                desiredState.getExtendsProcessId(),
                desiredState.getTools(),
                desiredState.getRoles(),
                desiredState.getWorkProducts(),
                desiredState.getTasks());

        validator.validate(existing);
        return repository.save(existing);
    }
}
