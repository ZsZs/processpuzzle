/**
 * Base Starter: the Business Starter catalog, and installing a starter from it into one organization.
 * A starter is a bundle — a zip of definition files in each feature's own seed YAML format — that CI
 * publishes to the public {@code processpuzzle-starters} bucket; the bucket's {@code catalog.json} is
 * the registry, read through the {@code StarterRegistry} port. Installing replaces the organization's
 * definitions and records which starter each one came from.
 * See {@code docs/business-starters/business-starters-design.md}.
 *
 * <p>Depends on no feature module. Each feature contributes a
 * {@link com.processpuzzle.core.definition.DefinitionImportParticipant} for its own definition kind,
 * and an {@link com.processpuzzle.core.definition.InstanceDataProbe} for the instance data it holds;
 * this module collects them as beans and runs the participants in order inside one transaction.
 * Billing for starters lives in Admin.
 */
@ApplicationModule(
        displayName = "Base Starter",
        allowedDependencies = {"core", "shared"})
package com.processpuzzle.starter;

import org.springframework.modulith.ApplicationModule;
