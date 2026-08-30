package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.workflow.common.ConflictException;
import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.definition.domain.Workflow;
import com.processpuzzle.workflow.definition.domain.WorkflowExtendsValidator;
import com.processpuzzle.workflow.definition.domain.WorkflowRepository;
import com.processpuzzle.workflow.definition.domain.WorkflowValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Full replace of a workflow definition's content. {@code desiredState.getVersion()} must match
 * the stored version for JPA optimistic locking to reject lost updates — the caller is expected
 * to have read the current version via GET first, per "Use version to prevent lost updates" in
 * base-workflow-api.yaml.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class ReplaceWorkflowUseCase {

    private final WorkflowRepository repository;
    private final WorkflowValidator validator;
    private final WorkflowExtendsValidator extendsValidator;

    public Workflow replace(String orgKey, String id, Workflow desiredState) {
        Workflow existing = repository.findByOrgKeyAndId(orgKey, id)
                .orElseThrow(() -> new NotFoundException("No workflow definition with id '%s'".formatted(id)));

        if (!id.equals(desiredState.getId())) {
            throw new ConflictException("id is immutable — cannot rename '%s' to '%s'".formatted(id, desiredState.getId()));
        }
        if (desiredState.getVersion() != null && !desiredState.getVersion().equals(existing.getVersion())) {
            throw new ConflictException(
                    "Workflow '%s' was modified concurrently — reload and retry".formatted(id));
        }

        extendsValidator.validate(orgKey, id, desiredState.getExtendsWorkflowId());

        existing.replaceContent(
                desiredState.getName(),
                desiredState.getDescription(),
                desiredState.getExtendsWorkflowId(),
                desiredState.getStartCondition(),
                desiredState.getRoles(),
                desiredState.getArtifacts(),
                desiredState.getTools(),
                desiredState.getTasks());

        validator.validate(existing);
        return repository.save(existing);
    }
}
