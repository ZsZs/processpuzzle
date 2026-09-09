package com.processpuzzle.orgadmin.adapters.inbound;

import com.processpuzzle.core.logging.LogClass;
import com.processpuzzle.orgadmin.api.OrganizationRolesApi;
import com.processpuzzle.orgadmin.model.OrganizationRole;
import com.processpuzzle.orgadmin.model.RoleAssignment;
import com.processpuzzle.orgadmin.usecases.inbound.FindOrganizationRoles;
import com.processpuzzle.orgadmin.usecases.inbound.FindOrganizationUserRoles;
import com.processpuzzle.orgadmin.usecases.inbound.ReplaceOrganizationUserRoles;
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

    private final FindOrganizationRoles findOrganizationRoles;
    private final FindOrganizationUserRoles findOrganizationUserRoles;
    private final ReplaceOrganizationUserRoles replaceOrganizationUserRoles;
    private final OrgAdminMapper mapper;

    public OrganizationRoleEndpoint(FindOrganizationRoles findOrganizationRoles,
                                    FindOrganizationUserRoles findOrganizationUserRoles,
                                    ReplaceOrganizationUserRoles replaceOrganizationUserRoles,
                                    OrgAdminMapper mapper) {
        this.findOrganizationRoles = findOrganizationRoles;
        this.findOrganizationUserRoles = findOrganizationUserRoles;
        this.replaceOrganizationUserRoles = replaceOrganizationUserRoles;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<List<OrganizationRole>> listOrganizationRoles(String orgKey) {
        return ResponseEntity.ok(mapper.toRoleList(findOrganizationRoles.execute(orgKey)));
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
