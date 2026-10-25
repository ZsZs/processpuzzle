package com.processpuzzle.orgadmin.adapters.inbound;

import com.processpuzzle.core.logging.LogClass;
import com.processpuzzle.orgadmin.api.SelfServiceApi;
import com.processpuzzle.orgadmin.model.RoleAssignment;
import com.processpuzzle.orgadmin.model.SelfServiceRoles;
import com.processpuzzle.orgadmin.usecases.inbound.FindMyRoles;
import com.processpuzzle.orgadmin.usecases.inbound.MyRoles;
import com.processpuzzle.orgadmin.usecases.inbound.ReplaceMyRoles;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** REST adapter for the caller's own role selection; acts on the token subject only. */
@RestController
@LogClass
public class SelfServiceRoleEndpoint implements SelfServiceApi {

    private final FindMyRoles findMyRoles;
    private final ReplaceMyRoles replaceMyRoles;
    private final OrgAdminMapper mapper;

    public SelfServiceRoleEndpoint(FindMyRoles findMyRoles, ReplaceMyRoles replaceMyRoles, OrgAdminMapper mapper) {
        this.findMyRoles = findMyRoles;
        this.replaceMyRoles = replaceMyRoles;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<SelfServiceRoles> getMyRoles(String orgKey) {
        return ResponseEntity.ok(toModel(findMyRoles.execute(orgKey)));
    }

    @Override
    public ResponseEntity<SelfServiceRoles> replaceMyRoles(String orgKey, RoleAssignment input) {
        return ResponseEntity.ok(toModel(replaceMyRoles.execute(orgKey, input.getRoles())));
    }

    private SelfServiceRoles toModel(MyRoles roles) {
        return new SelfServiceRoles(roles.assigned(), mapper.toRoleList(roles.selectable()));
    }
}
