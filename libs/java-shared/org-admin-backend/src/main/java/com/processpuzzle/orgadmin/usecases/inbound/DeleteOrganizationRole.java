package com.processpuzzle.orgadmin.usecases.inbound;

import com.processpuzzle.orgadmin.usecases.inbound.exception.RoleInUseException;
import com.processpuzzle.orgadmin.usecases.outbound.RoleCatalogPort;
import org.springframework.stereotype.Service;

/**
 * Deletes a role the administrator defined, once nothing depends on it.
 *
 * <p>Refuses while a user holds the role or a document, content book, navigation item or workflow still
 * names it, and says which: deleting it anyway would leave a document readable by nobody, or a menu
 * entry nobody can see. The check and the delete are not one transaction — see {@link RoleCatalogPort}.
 */
@Service
public class DeleteOrganizationRole {

    private final TenantRealmResolver realms;
    private final ManagedRoles managed;
    private final RoleCatalogPort catalog;

    DeleteOrganizationRole(TenantRealmResolver realms, ManagedRoles managed, RoleCatalogPort catalog) {
        this.realms = realms;
        this.managed = managed;
        this.catalog = catalog;
    }

    public void execute(String orgKey, String name) {
        String realm = realms.resolve(orgKey);
        ManagedRoles.Found found = managed.find(orgKey, realm, name);
        boolean held = catalog.hasHolders(realm, name);
        if (held || !found.references().isEmpty()) {
            throw new RoleInUseException(name, held, found.references());
        }
        catalog.deleteRole(realm, name);
    }
}
