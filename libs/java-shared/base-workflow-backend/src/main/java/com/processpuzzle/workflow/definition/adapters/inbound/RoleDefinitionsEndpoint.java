package com.processpuzzle.workflow.definition.adapters.inbound;

import com.processpuzzle.workflow.api.RoleDefinitionsApi;
import com.processpuzzle.workflow.definition.usecases.inbound.CreateRoleDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.DeleteRoleDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.FindAllRoleDefinitionsUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.FindRoleDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.ReplaceRoleDefinitionUseCase;
import com.processpuzzle.workflow.model.RoleDefinition;
import com.processpuzzle.workflow.model.RoleDefinitionInput;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Implements the generated {@code RoleDefinitionsApi} (from the "Role Definitions" tag).
 *
 * <p>Organization-scoped, not process-scoped: a role belongs to the tenant's catalog and may be
 * referenced by any number of process definitions.
 */
@RestController
public class RoleDefinitionsEndpoint implements RoleDefinitionsApi {

    private final CreateRoleDefinitionUseCase createRoleDefinition;
    private final ReplaceRoleDefinitionUseCase replaceRoleDefinition;
    private final DeleteRoleDefinitionUseCase deleteRoleDefinition;
    private final FindRoleDefinitionUseCase findRoleDefinition;
    private final FindAllRoleDefinitionsUseCase findAllRoleDefinitions;
    private final WorkflowDefinitionMapper mapper;

    public RoleDefinitionsEndpoint(CreateRoleDefinitionUseCase createRoleDefinition,
                                    ReplaceRoleDefinitionUseCase replaceRoleDefinition,
                                    DeleteRoleDefinitionUseCase deleteRoleDefinition,
                                    FindRoleDefinitionUseCase findRoleDefinition,
                                    FindAllRoleDefinitionsUseCase findAllRoleDefinitions,
                                    WorkflowDefinitionMapper mapper) {
        this.createRoleDefinition = createRoleDefinition;
        this.replaceRoleDefinition = replaceRoleDefinition;
        this.deleteRoleDefinition = deleteRoleDefinition;
        this.findRoleDefinition = findRoleDefinition;
        this.findAllRoleDefinitions = findAllRoleDefinitions;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<List<RoleDefinition>> listRoleDefinitions(String orgKey, String where, String order) {
        var roles = findAllRoleDefinitions.findAll(orgKey, where, order);
        return ResponseEntity.ok(roles.stream().map(mapper::toRoleModel).toList());
    }

    @Override
    public ResponseEntity<RoleDefinition> createRoleDefinition(String orgKey, RoleDefinitionInput input) {
        var created = createRoleDefinition.create(orgKey, mapper.toRoleDomain(input));
        return new ResponseEntity<>(mapper.toRoleModel(created), HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<RoleDefinition> getRoleDefinition(String orgKey, String roleId) {
        return ResponseEntity.ok(mapper.toRoleModel(findRoleDefinition.findByOrgKeyAndId(orgKey, roleId)));
    }

    @Override
    public ResponseEntity<RoleDefinition> updateRoleDefinition(String orgKey, String roleId, RoleDefinitionInput input) {
        var updated = replaceRoleDefinition.replace(orgKey, roleId, mapper.toRoleDomain(input));
        return ResponseEntity.ok(mapper.toRoleModel(updated));
    }

    @Override
    public ResponseEntity<Void> deleteRoleDefinition(String orgKey, String roleId) {
        deleteRoleDefinition.delete(orgKey, roleId);
        return ResponseEntity.noContent().build();
    }
}
