/**
 * Base Entity: runtime metadata-driven entity platform for ProcessPuzzle.
 *
 * <p>Owns entity definitions and attributes (knowledge layer) as well as entity instance
 * persistence and RSQL querying over JSONB payloads (operation layer).
 */
@ApplicationModule(
        displayName = "Base Entity",
        allowedDependencies = {"core", "shared"})
package com.processpuzzle.baseentity;

import org.springframework.modulith.ApplicationModule;
