/**
 * Cross-cutting infrastructure shared by every ProcessPuzzle feature: the logging aspect, the RSQL
 * query translator and the base REST exception handler.
 *
 * <p>Declared {@link org.springframework.modulith.ApplicationModule.Type#OPEN} on purpose. This is
 * not a feature module — it owns no domain and publishes no events — so its sub-packages are all
 * part of its API and it is exempt from dependency verification. Feature modules still have to name
 * {@code core} in their own {@code allowedDependencies}, which keeps the edge visible.
 */
@ApplicationModule(displayName = "ProcessPuzzle Core", type = ApplicationModule.Type.OPEN)
package com.processpuzzle.core;

import org.springframework.modulith.ApplicationModule;
