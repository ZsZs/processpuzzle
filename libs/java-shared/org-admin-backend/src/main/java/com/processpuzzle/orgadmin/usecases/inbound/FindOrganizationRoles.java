package com.processpuzzle.orgadmin.usecases.inbound;

import com.processpuzzle.core.tenancy.RoleReference;
import com.processpuzzle.orgadmin.usecases.outbound.DirectoryRole;
import com.processpuzzle.orgadmin.usecases.outbound.UserDirectoryPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * The roles a tenant declares, each with who manages it.
 *
 * <p>Read live from the directory rather than from a fixed enum. Beyond {@code org-admin} and
 * {@code org-member}, the roles are the tenant's own and are what {@code NavNode.roles} and workflow
 * role definitions are matched against — so an enum here would make adding a role a code change in
 * a platform whose whole premise is that extension is configuration.
 *
 * <p>The origin is derived, not stored: the identity provider's list omits attributes, and a stored
 * one would be erased by any update that did not resend it. A role is {@link RoleOrigin#PLATFORM} when
 * the platform or the provider owns its name, {@link RoleOrigin#WORKFLOW} when a workflow role
 * definition has that id, and {@link RoleOrigin#ADMIN} otherwise.
 */
@Service
public class FindOrganizationRoles {

    private final TenantRealmResolver realms;
    private final UserDirectoryPort directory;
    private final RoleReferences references;

    public FindOrganizationRoles(TenantRealmResolver realms, UserDirectoryPort directory, RoleReferences references) {
        this.realms = realms;
        this.directory = directory;
        this.references = references;
    }

    public List<CatalogRole> execute(String orgKey) {
        String realm = realms.resolve(orgKey);
        Map<String, List<RoleReference>> named = references.in(orgKey);
        return directory.findRoles(realm).stream().map(role -> classify(role, named)).toList();
    }

    static CatalogRole classify(DirectoryRole role, Map<String, List<RoleReference>> named) {
        return new CatalogRole(role.name(), role.description(), role.platformManaged(), originOf(role, named));
    }

    static RoleOrigin originOf(DirectoryRole role, Map<String, List<RoleReference>> named) {
        if (role.platformManaged() || RoleNames.isReserved(role.name())) {
            return RoleOrigin.PLATFORM;
        }
        boolean designed = named.getOrDefault(role.name(), List.of()).stream().anyMatch(RoleReference::isRoleDefinition);
        return designed ? RoleOrigin.WORKFLOW : RoleOrigin.ADMIN;
    }
}
