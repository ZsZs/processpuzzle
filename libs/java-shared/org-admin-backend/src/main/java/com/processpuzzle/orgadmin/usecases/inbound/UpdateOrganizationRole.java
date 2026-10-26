package com.processpuzzle.orgadmin.usecases.inbound;

import com.processpuzzle.orgadmin.usecases.outbound.RoleCatalogPort;
import org.springframework.stereotype.Service;

/**
 * Changes a role's description. Only the description: the name is what every reference and token
 * carries, so renaming would silently detach all of them.
 */
@Service
public class UpdateOrganizationRole {

    private final TenantRealmResolver realms;
    private final ManagedRoles managed;
    private final RoleCatalogPort catalog;

    UpdateOrganizationRole(TenantRealmResolver realms, ManagedRoles managed, RoleCatalogPort catalog) {
        this.realms = realms;
        this.managed = managed;
        this.catalog = catalog;
    }

    public CatalogRole execute(String orgKey, String name, String description) {
        String realm = realms.resolve(orgKey);
        CatalogRole role = managed.find(orgKey, realm, name).role();
        String text = description == null || description.isBlank() ? null : description.trim();
        catalog.updateDescription(realm, name, text);
        return new CatalogRole(role.name(), text, role.platformManaged(), role.origin());
    }
}
