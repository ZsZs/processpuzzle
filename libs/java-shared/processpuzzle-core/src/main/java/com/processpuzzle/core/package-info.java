/**
 * Cross-cutting infrastructure shared by every ProcessPuzzle feature: the logging aspect, the RSQL
 * query translator, the base REST exception handler, and the translation-bundle shape and importer
 * every feature's i18n resource is built from.
 *
 * <p>Declared {@link org.springframework.modulith.ApplicationModule.Type#OPEN} on purpose. This is
 * not a feature module — it owns no domain and publishes no events — so its sub-packages are all
 * part of its API and it is exempt from dependency verification. Feature modules still have to name
 * {@code core} in their own {@code allowedDependencies}, which keeps the edge visible.
 *
 * <p>The i18n package does not change that. It contributes a {@code @MappedSuperclass} and a classpath
 * scanner, not a table, a repository or an endpoint: each feature library declares its own entity
 * against its own table and serves its own resource, so no translation is owned here.
 */
@ApplicationModule(displayName = "ProcessPuzzle Core", type = ApplicationModule.Type.OPEN)
package com.processpuzzle.core;

import org.springframework.modulith.ApplicationModule;
