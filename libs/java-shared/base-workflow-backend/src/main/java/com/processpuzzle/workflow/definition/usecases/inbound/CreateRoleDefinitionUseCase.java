package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.workflow.common.ConflictException;
import com.processpuzzle.workflow.common.ValidationException;
import com.processpuzzle.workflow.definition.domain.RoleDefinition;
import com.processpuzzle.workflow.definition.domain.RoleDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adds a role to the organization's catalog. Nothing is checked against the workflows that may
 * later reference it: a role exists on its own, and it is
 * {@code WorkflowValidator} that refuses a workflow naming one that does not.
 *
 * <p>The id is refused when it names a reserved realm role. The role catalog is projected into the
 * organization's realm roles under the definition's id verbatim (see {@code SyncRoleDirectory}), so
 * this is the one place an author can be told that a name is unavailable — refusing with a 400 here
 * beats accepting the row and silently declining to project it, which would look like the projection
 * being broken.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class CreateRoleDefinitionUseCase {

    private final RoleDefinitionRepository repository;

    public RoleDefinition create(String orgKey, RoleDefinition role) {
        role.setOrgKey(orgKey);
        if (SyncRoleDirectory.isReserved(role.getId())) {
            throw new ValidationException(
                    "Role id '%s' is reserved by the platform or the identity provider — choose another"
                            .formatted(role.getId()));
        }
        if (repository.existsByOrgKeyAndIdAndChannel(orgKey, role.getId(), Channel.DRAFT)) {
            throw new ConflictException("Role definition '%s' already exists".formatted(role.getId()));
        }
        return repository.save(role);
    }
}
