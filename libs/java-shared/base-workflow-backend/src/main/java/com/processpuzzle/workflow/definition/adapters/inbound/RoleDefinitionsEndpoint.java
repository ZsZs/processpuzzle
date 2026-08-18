package com.processpuzzle.workflow.definition.adapters.inbound;

import com.processpuzzle.workflow.api.RoleDefinitionsApi;
import com.processpuzzle.workflow.definition.usecases.inbound.CreateRoleDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.DeleteRoleDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.FindProcessDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.ReplaceRoleDefinitionUseCase;
import com.processpuzzle.workflow.model.RoleDefinition;
import com.processpuzzle.workflow.model.RoleDefinitionInput;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Implements the generated {@code RoleDefinitionsApi} (from the "Role Definitions" tag). */
@RestController
public class RoleDefinitionsEndpoint implements RoleDefinitionsApi {

    private final CreateRoleDefinitionUseCase createRoleDefinition;
    private final ReplaceRoleDefinitionUseCase replaceRoleDefinition;
    private final DeleteRoleDefinitionUseCase deleteRoleDefinition;
    private final FindProcessDefinitionUseCase findProcessDefinition;
    private final WorkflowDefinitionMapper mapper;

    public RoleDefinitionsEndpoint(CreateRoleDefinitionUseCase createRoleDefinition,
                                    ReplaceRoleDefinitionUseCase replaceRoleDefinition,
                                    DeleteRoleDefinitionUseCase deleteRoleDefinition,
                                    FindProcessDefinitionUseCase findProcessDefinition,
                                    WorkflowDefinitionMapper mapper) {
        this.createRoleDefinition = createRoleDefinition;
        this.replaceRoleDefinition = replaceRoleDefinition;
        this.deleteRoleDefinition = deleteRoleDefinition;
        this.findProcessDefinition = findProcessDefinition;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<List<RoleDefinition>> listRoleDefinitions(String orgKey, String processId, String where, String order) {
        var process = findProcessDefinition.findByOrgKeyAndId(orgKey, processId);
        // TODO: 'where'/'order' are accepted per the API contract but not yet applied here.
        // RsqlSpecificationBuilder builds JPA Specifications against a query root; roles live
        // entirely inside the already-loaded process aggregate (see ProcessDefinitionRepository's
        // Javadoc for why there's no separate roles table/repository to query), so there's no
        // JPA query to attach a Specification to. Fine while a process has a handful of roles;
        // if that stops being true, either add a lightweight in-memory RSQL evaluator to
        // processpuzzle-core, or give RoleDefinition its own repository after all.
        List<RoleDefinition> roles = process.getRoles().stream().map(mapper::toRoleModel).toList();
        return ResponseEntity.ok(roles);
    }

    @Override
    public ResponseEntity<RoleDefinition> createRoleDefinition(String orgKey, String processId, RoleDefinitionInput input) {
        var created = createRoleDefinition.create(orgKey, processId, mapper.toRoleDomain(input));
        return new ResponseEntity<>(mapper.toRoleModel(created), HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<RoleDefinition> getRoleDefinition(String orgKey, String processId, String roleId) {
        var process = findProcessDefinition.findByOrgKeyAndId(orgKey, processId);
        var role = process.findRole(roleId)
                .orElseThrow(() -> new com.processpuzzle.workflow.common.NotFoundException(
                        "No role '%s' in process '%s'".formatted(roleId, processId)));
        return ResponseEntity.ok(mapper.toRoleModel(role));
    }

    @Override
    public ResponseEntity<RoleDefinition> updateRoleDefinition(String orgKey, String processId, String roleId, RoleDefinitionInput input) {
        var updated = replaceRoleDefinition.replace(orgKey, processId, roleId, mapper.toRoleDomain(input));
        return ResponseEntity.ok(mapper.toRoleModel(updated));
    }

    @Override
    public ResponseEntity<Void> deleteRoleDefinition(String orgKey, String processId, String roleId) {
        deleteRoleDefinition.delete(orgKey, processId, roleId);
        return ResponseEntity.noContent().build();
    }
}
