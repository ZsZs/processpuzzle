package com.processpuzzle.app.usecase.port;

/**
 * Development stand-in that permits everything, used when the deploying application supplies no
 * {@link OrganizationAccessPolicy} bean.
 *
 * <p><b>This enforces no tenant isolation.</b> It exists so the feature is usable — and testable —
 * before Keycloak is wired into the backend, and so that the 403 path and the role-filtering call
 * sites are in place rather than retrofitted. Any deployment serving more than one tenant must
 * provide a real implementation; see {@code OrganizationGuard} for how it is picked up.
 *
 * <p>Deliberately not a {@code @Component}: it is instantiated as a fallback by
 * {@code OrganizationGuard}, so a real policy bean never has to compete with it.
 */
public class PermitAllOrganizationAccessPolicy implements OrganizationAccessPolicy {
}
