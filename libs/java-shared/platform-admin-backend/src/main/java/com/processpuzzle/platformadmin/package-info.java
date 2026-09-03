/**
 * Platform Admin: the tenant itself, the identity realm behind it, and what it is billed.
 *
 * <p>Every other feature in ProcessPuzzle is scoped by an {@code orgKey}. This module is what
 * decides that an {@code orgKey} exists at all — it owns the {@link
 * com.processpuzzle.platformadmin.domain.Organization} aggregate, its lifecycle, and the
 * cross-tenant {@code /platform/**} surface ProcessPuzzle staff drive it from.
 *
 * <h2>It used to live in base-app</h2>
 *
 * <p>The aggregate started in {@code com.processpuzzle.app} because base-app was the first feature
 * that needed a tenant. That put the platform's own administration inside a feature library, which
 * meant base-app had to be deployed to create a customer and that suspending a tenant was an app
 * concern. The direction of the dependency is now the other way round: base-app consumes
 * {@code platformadmin :: usecase}, {@code :: port}, {@code :: domain}, {@code :: event} and
 * {@code :: exception}, and nothing here knows what an {@code AppDefinition} is.
 *
 * <p>Two knots had to be untied to get there, and both were untied without a cycle:
 *
 * <ul>
 *   <li>{@code ProvisionOrganization} created the starter app definition in the same transaction, so
 *       a client could never see an organization without an app. It now creates only the
 *       organization; {@code app.usecase.ProvisionTenant} is the {@code @Transactional} caller that
 *       adds the app, so the invariant is preserved on base-app's side of the line.
 *   <li>{@code DeleteOrganization} cascaded into base-app's repositories. It now publishes
 *       {@link com.processpuzzle.platformadmin.domain.event.OrganizationDeletedEvent}, which base-app
 *       observes with {@code BEFORE_COMMIT} so its deletes join the same transaction. This is what
 *       the old implementation's own Javadoc already recommended.
 * </ul>
 *
 * <p>The URL surface deliberately did not move with it. The five tenant-facing {@code /organizations*}
 * operations stay declared in {@code base-app-api.yaml} and stay served by {@code AppEndpoint}, now
 * delegating here. Ownership moved at the aggregate level, which is what was intended; splitting one
 * {@code useTags}-generated API interface across two controllers is not possible anyway.
 *
 * <h2>Realm creation is the one step with no rollback</h2>
 *
 * <p>A Keycloak realm cannot be created inside a database transaction, and one created for a
 * transaction that then rolls back is an orphan nothing knows about. So provisioning commits
 * {@code PROVISIONING} and creates the realm after commit, and suspend/activate/delete likewise do
 * their realm call after theirs. {@code PROVISIONING} is consequently a real, observable,
 * <em>retryable</em> state rather than the unreachable one it was while base-app owned the enum.
 *
 * <p>Only {@code core} and {@code shared} are allowed dependencies. Notably not {@code baseentity}:
 * users are not entity objects here — Keycloak is the system of record for them, and
 * {@code org-admin-backend} is the module that proxies it.
 */
@ApplicationModule(
        displayName = "Platform Admin",
        allowedDependencies = {"core", "shared"})
package com.processpuzzle.platformadmin;

import org.springframework.modulith.ApplicationModule;
