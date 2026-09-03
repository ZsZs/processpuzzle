package com.processpuzzle.platformadmin.usecase.port;

/**
 * Development stand-in that permits everything, used when the deploying application supplies no
 * {@link OrganizationAccessPolicy} bean.
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
 * <p>Deliberately not a {@code @Component}: it is instantiated as a fallback by
 * {@code OrganizationGuard}, so a real policy bean never has to compete with it.
 */
public class PermitAllOrganizationAccessPolicy implements OrganizationAccessPolicy {
}
