package com.processpuzzle.orgadmin.usecases.inbound;

import com.processpuzzle.orgadmin.usecases.outbound.DirectoryRole;
import com.processpuzzle.orgadmin.usecases.outbound.UserDirectoryPort;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The realm roles a tenant declares.
 *
 * <p>Read live from the realm rather than from a fixed enum. Beyond {@code org-admin} and
 * {@code org-member}, the roles are the tenant's own and are what {@code NavNode.roles} and workflow
 * role definitions are matched against — so an enum here would make adding a role a code change in
 * a platform whose whole premise is that extension is configuration.
 */
@Service
public class FindOrganizationRoles {

    private final TenantRealmResolver realms;
    private final UserDirectoryPort directory;

    public FindOrganizationRoles(TenantRealmResolver realms, UserDirectoryPort directory) {
        this.realms = realms;
        this.directory = directory;
    }

    public List<DirectoryRole> execute(String orgKey) {
        return directory.findRoles(realms.resolve(orgKey));
    }
}
