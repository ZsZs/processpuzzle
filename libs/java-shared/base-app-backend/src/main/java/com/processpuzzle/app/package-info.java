/**
 * Base App: the application shell feature. Interprets workspace, navigation and panel layout
 * definitions per organization, and produces the shell that hosts every other feature.
 *
 * <p>Depends on Base Rule's {@code usecase} named interface: beyond the structural checks in
 * {@link com.processpuzzle.app.usecase.service.AppDefinitionValidator}, an app definition is
 * validated against the organization's own {@code base-rule} records, so governance of what a
 * designer may publish is configuration rather than code.
 *
 * <h2>It no longer owns the tenant</h2>
 *
 * <p>{@code Organization} started here, because base-app was the first feature that needed a tenant.
 * It now belongs to {@code platform-admin}, and this module is a consumer of five of its named
 * interfaces:
 *
 * <ul>
 *   <li>{@code platformadmin :: usecase} — {@code OrganizationGuard} (every use case in this module
 *       calls it), {@code CheckOrganizationKey}, {@code FindOrganization},
 *       {@code UpdateOrganization}, {@code DeleteOrganization} and {@code ProvisionOrganization},
 *       which the five tenant-facing {@code /organizations*} operations still delegate to.
 *   <li>{@code platformadmin :: domain} — {@code Organization} itself, returned by the provisioning
 *       flow and read for a tenant's default locale.
 *   <li>{@code platformadmin :: port} — {@code OrganizationAccessPolicy}, which used to be one of
 *       this module's own ports.
 *   <li>{@code platformadmin :: event} — {@code OrganizationDeletedEvent}, observed by
 *       {@link com.processpuzzle.app.adapter.inbound.TenantDataCleaner}.
 *   <li>{@code platformadmin :: exception} — the four {@code Organization*} exceptions, which
 *       {@link com.processpuzzle.app.adapter.inbound.AppApiExceptionHandler} must name itself
 *       because advice scoping matches on the controller's package, not the exception's.
 * </ul>
 *
 * <p>The edge runs one way. Two things had to change on this side to keep it that way:
 * {@link com.processpuzzle.app.usecase.ProvisionTenant} now owns the transaction that writes an
 * organization and its starter app together, and
 * {@link com.processpuzzle.app.usecase.service.NavVisibilityFilter} holds the nav-tree filtering that
 * {@code OrganizationGuard} used to do — it walks this module's {@code Region}/{@code NavNode}, so it
 * could not travel with the guard.
 *
 * <p>Exposes {@code app :: usecase} (the app-definition use cases) and {@code app :: port} (the SPI
 * a host application implements to contribute entity names).
 */
@ApplicationModule(
        displayName = "Base App",
        allowedDependencies = {
                "core", "shared", "rule :: usecase", "rule :: domain",
                "platformadmin :: usecase", "platformadmin :: domain", "platformadmin :: port",
                "platformadmin :: event", "platformadmin :: exception"})
package com.processpuzzle.app;

import org.springframework.modulith.ApplicationModule;
