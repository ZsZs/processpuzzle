package com.processpuzzle.orgadmin.adapters.inbound;

import com.processpuzzle.core.logging.LogClass;
import com.processpuzzle.orgadmin.api.RoleCatalogApi;
import com.processpuzzle.orgadmin.model.OrganizationRole;
import com.processpuzzle.orgadmin.usecases.inbound.FindRoleCatalog;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** REST adapter for the roles an author may refer to; a separate controller because the generated interface is. */
@RestController
@LogClass
public class RoleCatalogEndpoint implements RoleCatalogApi {

    private final FindRoleCatalog findRoleCatalog;
    private final OrgAdminMapper mapper;

    public RoleCatalogEndpoint(FindRoleCatalog findRoleCatalog, OrgAdminMapper mapper) {
        this.findRoleCatalog = findRoleCatalog;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<List<OrganizationRole>> listRoleCatalog(String orgKey) {
        return ResponseEntity.ok(mapper.toRoleList(findRoleCatalog.execute(orgKey)));
    }
}
