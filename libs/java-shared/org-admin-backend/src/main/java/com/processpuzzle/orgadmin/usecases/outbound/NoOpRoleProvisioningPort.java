package com.processpuzzle.orgadmin.usecases.outbound;

import java.util.Collection;

/**
 * Stand-in when there is no identity provider to create roles in, or when roles are not realm roles
 * ({@code keycloak.admin.organization-realm}). Accepts silently: the roles are a projection of what
 * authors wrote, which is stored regardless, so there is nothing to refuse.
 *
 * <p>Deliberately not a {@code @Component}: {@code UserDirectoryConfiguration} registers it.
 */
public class NoOpRoleProvisioningPort implements RoleProvisioningPort {

    @Override
    public void ensureRoles(String realm, Collection<String> roleNames, String description) {
        // Nothing to create them in.
    }
}
