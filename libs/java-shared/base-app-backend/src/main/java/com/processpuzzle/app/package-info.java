/**
 * Base App: the application shell feature. Interprets workspace, navigation and panel layout
 * definitions per organization, and produces the shell that hosts every other feature.
 *
 * <p>Depends on Base Rule's {@code usecase} named interface: beyond the structural checks in
 * {@link com.processpuzzle.app.usecase.service.AppDefinitionValidator}, an app definition is
 * validated against the organization's own {@code base-rule} records, so governance of what a
 * designer may publish is configuration rather than code.
 *
 * <p>Exposes {@code app :: usecase} (the app-definition use cases) and {@code app :: port} (the SPI
 * a host application implements to contribute entity names and an organization access policy).
 */
@ApplicationModule(
        displayName = "Base App",
        allowedDependencies = {"core", "shared", "rule :: usecase", "rule :: domain"})
package com.processpuzzle.app;

import org.springframework.modulith.ApplicationModule;
