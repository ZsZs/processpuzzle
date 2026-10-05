/**
 * Base Starter: imports Business Starter bundles — zips of definition files in each feature's own seed
 * YAML format — into one organization, and records which starter each imported definition came from.
 * See {@code docs/business-starters/business-starters-design.md}.
 *
 * <p>Depends on no feature module. Each feature contributes a
 * {@link com.processpuzzle.core.definition.DefinitionImportParticipant} for its own definition kind;
 * this module collects them as beans and runs them in order inside one transaction. The catalog of
 * starters, and billing for them, live elsewhere (processpuzzle-biz and Admin).
 */
@ApplicationModule(
        displayName = "Base Starter",
        allowedDependencies = {"core", "shared"})
package com.processpuzzle.starter;

import org.springframework.modulith.ApplicationModule;
