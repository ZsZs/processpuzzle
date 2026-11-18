/**
 * Base Chat: conversations bound to platform subjects — an entity instance, a workflow instance —
 * with role-based access, history, read markers and live delivery.
 *
 * <p>The module knows nothing about entities, workflows or the identity provider. What it needs from
 * the rest of the platform — does the subject exist, who owns it, which state is it in — it declares
 * as outbound ports in {@code conversation :: usecases.outbound}; the host application supplies the
 * adapters. Subject lifecycle arrives as {@link com.processpuzzle.shared.event.PlatformEvent}s, never
 * as another feature's own event types.
 *
 * <p>Layers inside each part ({@code definition}, {@code conversation}) are {@code domain},
 * {@code usecases} and {@code adapters}; {@code HexagonalLayerRules} verifies their direction at build
 * time, alongside the Modulith module edges.
 */
@ApplicationModule(
        displayName = "Base Chat",
        allowedDependencies = {"core", "shared"})
package com.processpuzzle.chat;

import org.springframework.modulith.ApplicationModule;
