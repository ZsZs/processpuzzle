package com.processpuzzle.core.tenancy;

import java.util.Collection;

/**
 * Development stand-in that permits everything, selected only when the deploying application
 * explicitly registers it as an {@link OrganizationAccessPolicy} bean.
 *
 * <p><b>This enforces no tenant isolation, and no separation between a customer and ProcessPuzzle
 * staff.</b> It exists so the feature is usable — and testable — without Keycloak, and so that the
 * 403 path and the role-filtering call sites are in place rather than retrofitted.
 *
 * <p>The stakes rose when {@code requirePlatformAdmin} joined the port: with this stand-in in place,
 * {@code /platform/**} — which deletes tenants — is reachable by anyone who can reach the port at
 * all. Any deployment serving more than one tenant, or exposed beyond a developer's machine, must
 * provide a real implementation; see {@code OrganizationGuard} for how it is picked up, and
 * {@code apps/processpuzzle-testbed-backend}'s {@code SecurityConfig} for the one that ships.
 *
 * <p>Deliberately not a {@code @Component}: a host must opt in with a {@code @Bean} declaration.
 * It is never an automatic fallback for missing or ambiguous policy wiring.
 */
public class PermitAllOrganizationAccessPolicy implements OrganizationAccessPolicy {

    @Override
    public void requireAccess(String orgKey) {
        // Explicitly unrestricted in demonstration hosts.
    }

    @Override
    public void requireDesign(String orgKey) {
        // Explicitly unrestricted in demonstration hosts.
    }

    @Override
    public void requireAdministration(String orgKey) {
        // Explicitly unrestricted in demonstration hosts.
    }

    @Override
    public void requirePlatformAdmin() {
        // Explicitly unrestricted in demonstration hosts.
    }

    @Override
    public boolean hasAnyRole(Collection<String> requiredRoles) {
        return true;
    }
}
