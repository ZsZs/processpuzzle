package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.workflow.common.ConflictException;
import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.definition.domain.RoleDefinition;
import com.processpuzzle.workflow.definition.domain.RoleDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.event.RoleDefinitionChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Full replace of a role definition. Field-by-field onto the loaded row rather than saving the
 * incoming one, so that {@code orgKey}, {@code id} and the audit columns survive — the same
 * convention {@link ReplaceToolDefinitionUseCase} follows.
 *
 * <p>The id cannot change here, so there is no reserved-name check to repeat: only the description
 * the realm role carries can have moved, which is what the published event projects.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class ReplaceRoleDefinitionUseCase {

    private final RoleDefinitionRepository repository;
    private final ApplicationEventPublisher events;

    public RoleDefinition replace(String orgKey, String id, RoleDefinition desiredState) {
        RoleDefinition existing = repository.findByOrgKeyAndId(orgKey, id)
                .orElseThrow(() -> new NotFoundException("No role definition with id '%s'".formatted(id)));

        if (desiredState.getVersion() != null && !desiredState.getVersion().equals(existing.getVersion())) {
            throw new ConflictException(
                    "Role definition '%s' was modified concurrently — reload and retry".formatted(id));
        }

        existing.setName(desiredState.getName());
        existing.setDescription(desiredState.getDescription());
        existing.setResponsibleFor(desiredState.getResponsibleFor());
        existing.setEntityRoleId(desiredState.getEntityRoleId());
        RoleDefinition replaced = repository.save(existing);
        events.publishEvent(new RoleDefinitionChangedEvent(
                orgKey, replaced.getId(), replaced.getName(), replaced.getDescription()));
        return replaced;
    }
}
