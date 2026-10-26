package com.processpuzzle.orgadmin.usecases.inbound;

import com.processpuzzle.core.tenancy.RoleReference;
import com.processpuzzle.orgadmin.usecases.inbound.exception.RoleNotFoundException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.RoleNotManagedException;
import com.processpuzzle.orgadmin.usecases.outbound.UserDirectoryPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Finds a role the administrator is allowed to change, refusing the ones that are not theirs.
 *
 * <p>Shared by the update and the delete, which ask exactly the same question first: the role has to
 * exist, and has to be one the administrator defined rather than one the platform or the designer owns.
 */
@Component
class ManagedRoles {

    private final UserDirectoryPort directory;
    private final RoleReferences references;

    ManagedRoles(UserDirectoryPort directory, RoleReferences references) {
        this.directory = directory;
        this.references = references;
    }

    /** The role, with every reference that names it. */
    Found find(String orgKey, String realm, String name) {
        Map<String, List<RoleReference>> named = references.in(orgKey);
        CatalogRole role = directory.findRoles(realm).stream()
                .filter(candidate -> candidate.name().equals(name))
                .map(candidate -> FindOrganizationRoles.classify(candidate, named))
                .findFirst()
                .orElseThrow(() -> new RoleNotFoundException(realm, name));
        if (role.origin() != RoleOrigin.ADMIN) {
            throw new RoleNotManagedException(name, role.origin() == RoleOrigin.WORKFLOW
                    ? "a workflow role definition, which is edited in the designer" : "ProcessPuzzle");
        }
        return new Found(role, named.getOrDefault(name, List.of()));
    }

    record Found(CatalogRole role, List<RoleReference> references) {
    }
}
