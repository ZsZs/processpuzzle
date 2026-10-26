package com.processpuzzle.orgadmin.adapters.inbound;

import com.processpuzzle.core.logging.LogClass;
import com.processpuzzle.orgadmin.api.OrganizationRolesApi;
import com.processpuzzle.orgadmin.model.NewOrganizationRole;
import com.processpuzzle.orgadmin.model.OrganizationRole;
import com.processpuzzle.orgadmin.model.OrganizationRoleUpdate;
import com.processpuzzle.orgadmin.model.RoleAssignment;
import com.processpuzzle.orgadmin.usecases.inbound.CatalogRole;
import com.processpuzzle.orgadmin.usecases.inbound.CreateOrganizationRole;
import com.processpuzzle.orgadmin.usecases.inbound.DeleteOrganizationRole;
import com.processpuzzle.orgadmin.usecases.inbound.FindOrganizationRoles;
import com.processpuzzle.orgadmin.usecases.inbound.FindOrganizationUserRoles;
import com.processpuzzle.orgadmin.usecases.inbound.ReplaceOrganizationUserRoles;
import com.processpuzzle.orgadmin.usecases.inbound.UpdateOrganizationRole;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST adapter for a tenant's realm roles and their assignment.
 *
 * <p>A separate controller from {@link OrganizationUserEndpoint} because the generated interfaces are
 * separate — and they both declare a {@code default getRequest()}, so one class implementing both
 * does not compile.
 */
@RestController
@LogClass
public class OrganizationRoleEndpoint implements OrganizationRolesApi {

    private final CreateOrganizationRole createOrganizationRole;
    private final UpdateOrganizationRole updateOrganizationRole;
    private final DeleteOrganizationRole deleteOrganizationRole;
    private final FindOrganizationRoles findOrganizationRoles;
    private final FindOrganizationUserRoles findOrganizationUserRoles;
    private final ReplaceOrganizationUserRoles replaceOrganizationUserRoles;
    private final OrgAdminMapper mapper;

    public OrganizationRoleEndpoint(CreateOrganizationRole createOrganizationRole,
                                    UpdateOrganizationRole updateOrganizationRole,
                                    DeleteOrganizationRole deleteOrganizationRole,
                                    FindOrganizationRoles findOrganizationRoles,
                                    FindOrganizationUserRoles findOrganizationUserRoles,
                                    ReplaceOrganizationUserRoles replaceOrganizationUserRoles,
                                    OrgAdminMapper mapper) {
        this.createOrganizationRole = createOrganizationRole;
        this.updateOrganizationRole = updateOrganizationRole;
        this.deleteOrganizationRole = deleteOrganizationRole;
        this.findOrganizationRoles = findOrganizationRoles;
        this.findOrganizationUserRoles = findOrganizationUserRoles;
        this.replaceOrganizationUserRoles = replaceOrganizationUserRoles;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<List<OrganizationRole>> listOrganizationRoles(String orgKey) {
        return ResponseEntity.ok(mapper.toCatalogList(findOrganizationRoles.execute(orgKey)));
    }

    @Override
    public ResponseEntity<OrganizationRole> createOrganizationRole(String orgKey, NewOrganizationRole input) {
        CatalogRole created = createOrganizationRole.execute(orgKey, input.getName(), input.getDescription());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toModel(created));
    }

    @Override
    public ResponseEntity<OrganizationRole> updateOrganizationRole(
            String orgKey, String roleName, OrganizationRoleUpdate input) {
        return ResponseEntity.ok(mapper.toModel(
                updateOrganizationRole.execute(orgKey, roleName, input.getDescription())));
    }

    @Override
    public ResponseEntity<Void> deleteOrganizationRole(String orgKey, String roleName) {
        deleteOrganizationRole.execute(orgKey, roleName);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<OrganizationRole>> getOrganizationUserRoles(String orgKey, String userId) {
        return ResponseEntity.ok(mapper.toRoleList(findOrganizationUserRoles.execute(orgKey, userId)));
    }

    @Override
    public ResponseEntity<List<OrganizationRole>> replaceOrganizationUserRoles(
            String orgKey, String userId, RoleAssignment input) {
        return ResponseEntity.ok(mapper.toRoleList(
                replaceOrganizationUserRoles.execute(orgKey, userId, input.getRoles())));
    }
}
