/**
 * Org Admin: user and role management inside one organization, for that organization's own
 * administrator. The inside-out counterpart of {@code platform-admin}, which administers tenants
 * from the outside.
 *
 * <h2>There is no user table</h2>
 *
 * <p>Keycloak is the system of record for users, and this module persists nothing — it proxies the
 * Keycloak Admin API for the realm named by {@code orgKey}, behind one outbound port
 * ({@link com.processpuzzle.orgadmin.usecases.outbound.UserDirectoryPort}). Three consequences are
 * worth stating because they surprise people:
 *
 * <ul>
 *   <li>A role granted here lands in the user's token on <em>next login</em>, not immediately. The
 *       API answers with the new role list at once, but an already-issued token keeps the old one.
 *   <li>Deleting an organization orphans no rows here, because there are none.
 *   <li>A failure from the directory means the operation did not happen. There is no local copy that
 *       could have drifted out of step, which is why every such failure is a retryable 503 rather
 *       than a 500 with unknown state behind it.
 * </ul>
 *
 * <h2>Why it depends on platform-admin</h2>
 *
 * <p>Serving a request means answering "which realm is this?" and refusing unknown or suspended
 * tenants before the directory is touched at all. That is {@code FindOrganization}'s job, so the
 * dependency runs org-admin → platform-admin and through named interfaces only:
 *
 * <ul>
 *   <li>{@code platformadmin :: usecase} — {@code FindOrganization} and {@code OrganizationGuard}.
 *   <li>{@code platformadmin :: domain} — {@code OrganizationStatus}, to recognise a suspended tenant.
 *   <li>{@code platformadmin :: exception} — {@code OrganizationNotFoundException} and
 *       {@code OrganizationAccessDeniedException}, which this module's own advice must name because
 *       {@code @RestControllerAdvice(basePackages = ...)} matches on the controller's package.
 *   <li>{@code platformadmin :: keycloak} — the shared admin client. A second one would mean a second
 *       token cache and a second copy of {@code keycloak.admin.*} free to drift; see that package.
 * </ul>
 *
 * <p>There is no reverse edge, and there must not be: platform-admin knows nothing about users.
 */
@ApplicationModule(
        displayName = "Org Admin",
        allowedDependencies = {
                "core", "shared",
                "platformadmin :: usecase", "platformadmin :: domain",
                "platformadmin :: exception", "platformadmin :: keycloak"})
package com.processpuzzle.orgadmin;

import org.springframework.modulith.ApplicationModule;
