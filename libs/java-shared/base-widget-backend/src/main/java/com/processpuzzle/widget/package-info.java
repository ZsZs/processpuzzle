/**
 * Base Widget: the catalogue of widget <em>types</em>. Owns {@code WidgetDefinition} — a registry
 * key, the JSON Schema of the widget's props, and the ports it offers — per organization.
 *
 * <p>Distinct from a widget <em>instance</em>: {@code WidgetInstance} in shared-api.yaml is one
 * placement of a type inside a container, and belongs to whichever module owns that container
 * (base-app for a page or region, base-document for a WIDGET block). The join is
 * {@code WidgetInstance.type == WidgetDefinition.key}.
 *
 * <p>Widgets are building blocks; apps and documents are aggregators. This module therefore sits
 * below both and depends on neither — no {@code app} or {@code document} entry appears in
 * {@code allowedDependencies}, and none may be added. It holds no widget implementations either:
 * those are Angular components registered into the frontend registry by {@code provideWidget()}.
 *
 * <p>Exposes {@code widget :: usecase} (the definition use cases) and {@code widget :: domain}
 * (the {@code WidgetDefinitionStatus} enum alone). The repository and the JPA entity stay internal,
 * the same way base-rule keeps its repository and rule engine internal.
 */
@ApplicationModule(
        displayName = "Base Widget",
        allowedDependencies = {"core", "shared"})
package com.processpuzzle.widget;

import org.springframework.modulith.ApplicationModule;
