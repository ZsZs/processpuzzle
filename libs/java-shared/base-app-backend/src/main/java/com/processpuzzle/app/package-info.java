/**
 * Base App: the application shell feature. Interprets workspace, navigation and panel layout
 * definitions per organization, and produces the shell that hosts every other feature.
 *
 * <h2>It depends on no other feature</h2>
 *
 * <p>It used to depend on two. {@code Organization} started here, because base-app was the first
 * feature that needed a tenant; when the aggregate moved to {@code platform-admin} the code followed
 * but the contract did not, so this module went on serving {@code /organizations*} by delegating into
 * that module's use cases, reading its {@code OrganizationRepository} for an existence check and a
 * locale, and naming its exception types in its own advice. Separately it called
 * {@code base-rule}'s {@code EvaluateObject} directly. Twenty-four of this module's source files
 * named {@code platformadmin}, and base-app could not be deployed without either library present.
 *
 * <p>Both edges are now outbound ports, in {@code app :: port}, answered by adapters in the
 * application rather than in this library:
 *
 * <ul>
 *   <li>{@link com.processpuzzle.app.usecase.port.TenantDirectory} — does this tenant exist, and what
 *       is its default locale. A two-field projection, not an aggregate.
 *   <li>{@link com.processpuzzle.app.usecase.port.RuleEvaluator} — what do this tenant's own
 *       governance rules say about a candidate definition.
 *   <li>{@link com.processpuzzle.app.usecase.port.EntityNameRegistry} — which entity names exist, the
 *       port this module always had.
 * </ul>
 *
 * <p>All three permit by default, so an application that wires none of them still runs: a library
 * that cannot answer a question must not answer it with "no". The tenant-lifecycle reactions run the
 * other way, through events rather than ports — {@code StarterAppCreator} creates a new tenant's
 * first application and {@code TenantDataCleaner} removes a deleted tenant's rows, both observing
 * {@code com.processpuzzle.shared.event} records that name no publisher.
 *
 * <p>What remains of the move: {@link com.processpuzzle.app.usecase.service.NavVisibilityFilter}
 * holds the nav-tree filtering {@code OrganizationGuard} used to do. It walks this module's
 * {@code Region}/{@code NavNode}, so it could not travel with the guard to
 * {@code processpuzzle-core}.
 *
 * <p>Exposes {@code app :: usecase} (the app-definition use cases) and {@code app :: port} (the SPI
 * a host application implements).
 */
@ApplicationModule(
        displayName = "Base App",
        allowedDependencies = {"core", "shared"})
package com.processpuzzle.app;

import org.springframework.modulith.ApplicationModule;
