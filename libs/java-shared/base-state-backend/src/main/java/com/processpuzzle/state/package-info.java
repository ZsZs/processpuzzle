/**
 * Base State: one state machine per entity type. Interprets persisted {@link
 * com.processpuzzle.state.domain.StateMachineDefinition} records — states, transitions,
 * triggers, guards and actions — to answer which transitions an {@code EntityObject} may take and
 * to be the single writer of its current-state attribute. No parallel or nested states in this
 * version.
 *
 * <p>Depends on {@code baseentity}, in that direction and not the reverse. A state machine names
 * an {@code entityName} — base-entity's {@code entityDefinitionCode} — and a {@code
 * stateAttributeKey} that must be a TEXT- or ENUM-valued attribute declared on that entity's
 * definition, which is what restricts state machines to entity types base-entity-backend manages.
 * Keeping the edge this way round is what keeps base-state the <em>only</em> writer of that
 * attribute: base-entity neither knows nor cares that some of its attributes are state-bearing, so
 * there is no second path by which a state could be written. The price is that
 * base-state-backend is no longer usable standalone.
 *
 * <p>Two named interfaces are used, and nothing else. {@code baseentity :: operations} supplies the
 * reads and the single-attribute compare-and-swap write, reached through this module's own
 * outbound port {@link com.processpuzzle.state.usecase.port.EntityObjectGateway} — the port stays,
 * because a host application may still substitute its own store, and {@link
 * com.processpuzzle.state.usecase.port.UnavailableEntityObjectGateway} remains the fallback when
 * neither is present. {@code baseentity :: event} supplies the lifecycle events; this module
 * observes {@code EntityObjectCreatedEvent} only, to write the initial state onto a new object.
 * Observing {@code EntityObjectUpdatedEvent} would be a loop, since writing the attribute is itself
 * an update.
 *
 * <p>Exposes {@code basestate :: usecase} (the state machine definition and transition use cases)
 * and {@code basestate :: port} (the SPI a host application implements to substitute
 * {@code EntityObject} reads/writes, and to supply named {@code TransitionGuard}/
 * {@code TransitionAction} beans).
 */
@ApplicationModule(
    displayName = "Base State",
    allowedDependencies = {"core", "shared", "baseentity :: operations", "baseentity :: event"})
package com.processpuzzle.state;

import org.springframework.modulith.ApplicationModule;
