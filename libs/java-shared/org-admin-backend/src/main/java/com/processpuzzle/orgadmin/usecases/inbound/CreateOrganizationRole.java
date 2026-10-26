package com.processpuzzle.orgadmin.usecases.inbound;

import com.processpuzzle.orgadmin.usecases.inbound.exception.InvalidRoleNameException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.RoleAlreadyExistsException;
import com.processpuzzle.orgadmin.usecases.outbound.RoleCatalogPort;
import com.processpuzzle.orgadmin.usecases.outbound.UserDirectoryPort;
import org.springframework.stereotype.Service;

/**
 * Defines a role of the organization, so that users can be given it and documents, navigation items and
 * workflows can refer to it.
 *
 * <p>Refuses a malformed name and every name the platform or the identity provider owns — an
 * administrator must not mint authority by typing {@code platform-admin} — and a name the organization
 * already declares, so that creating is never silently an update.
 */
@Service
public class CreateOrganizationRole {

    private final TenantRealmResolver realms;
    private final UserDirectoryPort directory;
    private final RoleCatalogPort catalog;

    public CreateOrganizationRole(TenantRealmResolver realms, UserDirectoryPort directory, RoleCatalogPort catalog) {
        this.realms = realms;
        this.directory = directory;
        this.catalog = catalog;
    }

    public CatalogRole execute(String orgKey, String name, String description) {
        String realm = realms.resolve(orgKey);
        if (!RoleNames.isValid(name)) {
            throw new InvalidRoleNameException(name, "use lower case letters, digits, '-' and '_', starting with a "
                    + "letter or digit, up to 63 characters.");
        }
        if (RoleNames.isReserved(name)) {
            throw new InvalidRoleNameException(name, "the name belongs to ProcessPuzzle or the identity provider.");
        }
        if (directory.findRoles(realm).stream().anyMatch(role -> role.name().equals(name))) {
            throw new RoleAlreadyExistsException(realm, name);
        }
        String text = description == null || description.isBlank() ? null : description.trim();
        catalog.createRole(realm, name, text);
        return new CatalogRole(name, text, false, RoleOrigin.ADMIN);
    }
}
