/**
 * The events a run publishes, for the workflow and state engines: {@link
 * com.processpuzzle.calculation.event.CalculationCompleted}, {@link
 * com.processpuzzle.calculation.event.CalculationFailed} and {@link
 * com.processpuzzle.calculation.event.CalculationFrozen}. Each is also published as a {@code PlatformEvent} of subject
 * type {@code calculation-run}, entering state {@code COMPLETED}, {@code FAILED} or {@code FROZEN}, which base-event's
 * catalog can name — so a workflow can start on a calculation without compiling against this module.
 *
 * <p>Published in the run's transaction; observe them with {@code @TransactionalEventListener}.
 */
@NamedInterface("event")
package com.processpuzzle.calculation.event;

import org.springframework.modulith.NamedInterface;
