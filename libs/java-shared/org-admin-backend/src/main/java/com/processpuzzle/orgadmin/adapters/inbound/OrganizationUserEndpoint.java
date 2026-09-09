package com.processpuzzle.orgadmin.adapters.inbound;

import com.processpuzzle.core.logging.LogClass;
import com.processpuzzle.orgadmin.api.OrganizationUsersApi;
import com.processpuzzle.orgadmin.model.OrganizationUser;
import com.processpuzzle.orgadmin.model.OrganizationUserInvite;
import com.processpuzzle.orgadmin.model.OrganizationUserUpdate;
import com.processpuzzle.orgadmin.model.PageOfOrganizationUser;
import com.processpuzzle.orgadmin.usecases.inbound.DeleteOrganizationUser;
import com.processpuzzle.orgadmin.usecases.inbound.FindOrganizationUser;
import com.processpuzzle.orgadmin.usecases.inbound.FindOrganizationUsers;
import com.processpuzzle.orgadmin.usecases.inbound.InviteOrganizationUser;
import com.processpuzzle.orgadmin.usecases.inbound.UpdateOrganizationUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for a tenant's user directory. Holds no logic of its own: it delegates to a use case
 * and maps the result.
 *
 * <p>Every operation resolves the tenant through {@code TenantRealmResolver} inside its use case,
 * which is where the membership check, the unknown-tenant 404 and the suspended-tenant refusal live.
 * Nothing is authorized in this class — the {@code orgKey} path segment reaching a handler is not
 * evidence that the caller may use it.
 */
@RestController
@LogClass
public class OrganizationUserEndpoint implements OrganizationUsersApi {

    private final FindOrganizationUsers findOrganizationUsers;
    private final FindOrganizationUser findOrganizationUser;
    private final InviteOrganizationUser inviteOrganizationUser;
    private final UpdateOrganizationUser updateOrganizationUser;
    private final DeleteOrganizationUser deleteOrganizationUser;
    private final OrgAdminMapper mapper;

    public OrganizationUserEndpoint(FindOrganizationUsers findOrganizationUsers,
                                    FindOrganizationUser findOrganizationUser,
                                    InviteOrganizationUser inviteOrganizationUser,
                                    UpdateOrganizationUser updateOrganizationUser,
                                    DeleteOrganizationUser deleteOrganizationUser,
                                    OrgAdminMapper mapper) {
        this.findOrganizationUsers = findOrganizationUsers;
        this.findOrganizationUser = findOrganizationUser;
        this.inviteOrganizationUser = inviteOrganizationUser;
        this.updateOrganizationUser = updateOrganizationUser;
        this.deleteOrganizationUser = deleteOrganizationUser;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<PageOfOrganizationUser> listOrganizationUsers(String orgKey, String search,
                                                                        Integer page, Integer size) {
        return ResponseEntity.ok(mapper.toModel(
                findOrganizationUsers.execute(orgKey, search, page, size)));
    }

    @Override
    public ResponseEntity<OrganizationUser> getOrganizationUser(String orgKey, String userId) {
        return ResponseEntity.ok(mapper.toModel(findOrganizationUser.execute(orgKey, userId)));
    }

    @Override
    public ResponseEntity<OrganizationUser> inviteOrganizationUser(String orgKey,
                                                                   OrganizationUserInvite input) {
        return new ResponseEntity<>(mapper.toModel(inviteOrganizationUser.execute(
                orgKey, mapper.toNewUser(input), input.getRoles())), HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<OrganizationUser> updateOrganizationUser(String orgKey, String userId,
                                                                   OrganizationUserUpdate input) {
        return ResponseEntity.ok(mapper.toModel(updateOrganizationUser.execute(
                orgKey, userId, mapper.toProfile(input))));
    }

    @Override
    public ResponseEntity<Void> deleteOrganizationUser(String orgKey, String userId) {
        deleteOrganizationUser.execute(orgKey, userId);
        return ResponseEntity.noContent().build();
    }
}
