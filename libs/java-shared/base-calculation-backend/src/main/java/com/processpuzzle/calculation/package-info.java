/**
 * Base Calculation: declarative calculations over entity data — row derivations, aggregates,
 * rankings and decision tables — defined as YAML with DMN semantics and evaluated with FEEL.
 *
 * <p>A {@link com.processpuzzle.calculation.domain.CalculationDefinition} is a small graph of
 * decisions over named inputs. The engine evaluates it in dependency order behind
 * {@code ExpressionEnginePort}, whose adapter is Camunda's feel-scala; a run writes the declared output
 * into its entity and keeps results, trace and a snapshot of the definition, so a frozen result stays
 * explainable after the definition changes.
 *
 * <p>Entity data is reached through the outbound ports of {@code calculation :: port}, which the host
 * application implements over base-entity — this module names no other feature. Runs are triggered
 * from the raw {@link com.processpuzzle.shared.event.PlatformEvent}s base-entity and base-state publish,
 * and announce themselves through {@code calculation :: event} and a {@code PlatformEvent} of subject
 * type {@code calculation-run}.
 */
@ApplicationModule(
        displayName = "Base Calculation",
        allowedDependencies = {"core", "shared"})
package com.processpuzzle.calculation;

import org.springframework.modulith.ApplicationModule;
