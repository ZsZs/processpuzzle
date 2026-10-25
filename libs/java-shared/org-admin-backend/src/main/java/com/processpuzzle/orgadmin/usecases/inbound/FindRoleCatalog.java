package com.processpuzzle.orgadmin.usecases.inbound;

import com.processpuzzle.orgadmin.usecases.outbound.DirectoryRole;
import com.processpuzzle.orgadmin.usecases.outbound.UserDirectoryPort;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The roles an author may refer to — in a document's editor roles, a navigation item, a workflow —
 * which is the realm's roles minus the ones the platform and the identity provider own.
 *
 * <p>The same definition of "selectable" as My Profile uses ({@link SelfServiceRoleSupport}), so an
 * author is never offered a role a user could not hold. Unlike {@link FindOrganizationRoles} this is
 * a read for any member, and so it must not list administrative roles.
 */
@Service
public class FindRoleCatalog {

    private final TenantRealmResolver realms;
    private final UserDirectoryPort directory;

    public FindRoleCatalog(TenantRealmResolver realms, UserDirectoryPort directory) {
        this.realms = realms;
        this.directory = directory;
    }

    public List<DirectoryRole> execute(String orgKey) {
        return directory.findRoles(realms.resolve(orgKey)).stream()
                .filter(SelfServiceRoleSupport::isSelectable)
                .toList();
    }
}
