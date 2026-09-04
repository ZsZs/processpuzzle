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
 * <h2>It depends on no other feature</h2>
 *
 * <p>Serving a request still means answering "which realm is this?" and refusing unknown or
 * suspended tenants before the directory is touched at all. That was {@code FindOrganization}'s job,
 * and the dependency ran org-admin → platform-admin: the only place in this platform where one
 * feature library compiled against another's use case. It is now a second outbound port,
 * {@link com.processpuzzle.orgadmin.usecases.outbound.TenantRealmDirectory}, and the two refusals
 * are unchanged.
 *
 * <p>What that buys is not tidiness. platform-admin is commercial and is moving to a private
 * repository; org-admin is not, because administering the users of one tenant is a platform feature.
 * The edge was the one thing making them inseparable. A deployment with a tenant registry supplies
 * an adapter over it — for the commercial product, in the private repository's composition root —
 * and a deployment without one gets {@code BY_CONVENTION}, which is the correct answer rather than a
 * degraded one wherever realm name and organization key are the same string.
 *
 * <p>The Keycloak admin client went the same way earlier. It was {@code platformadmin :: keycloak},
 * so that both modules shared one token cache; it is now
 * {@link com.processpuzzle.core.identity.KeycloakAdminClient} and reached through core, which gives
 * the same single cache without an edge between two features.
 *
 * <p>There was never a reverse edge, and there must not be: platform-admin knows nothing about users.
 */
@ApplicationModule(displayName = "Org Admin", allowedDependencies = {"core", "shared"})
package com.processpuzzle.orgadmin;

import org.springframework.modulith.ApplicationModule;
