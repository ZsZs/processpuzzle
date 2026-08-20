/**
 * Base State: one state machine per entity type. Interprets persisted {@link
 * com.processpuzzle.state.domain.StateMachineDefinition} records — states, transitions,
 * triggers, guards and actions — to answer which transitions an {@code EntityObject} may take and
 * to be the single writer of its current-state attribute. No parallel or nested states in this
 * version.
 *
 * <p>Deliberately does <em>not</em> depend on {@code entity}: base-entity-backend has not yet
 * implemented the operation layer that stores {@code EntityObject} instances (only the knowledge-
 * layer descriptor scaffold exists today), so there is nothing concrete to depend on yet. Instead
 * this module owns an outbound port, {@link
 * com.processpuzzle.state.usecase.port.EntityObjectGateway}, that whichever module ends up
 * hosting {@code EntityObject} persistence implements — the same dependency-inversion shape as
 * {@code app :: port}'s {@code EntityNameRegistry}. Until that adapter exists, the operation-layer
 * use cases ({@code GetEntityObjectState}, {@code FireStateTransition}) fail loudly at call time
 * rather than silently no-op — see {@link
 * com.processpuzzle.state.usecase.port.UnavailableEntityObjectGateway}.
 *
 * <p>Exposes {@code basestate :: usecase} (the state machine definition and transition use cases)
 * and {@code basestate :: port} (the SPI a host application, or base-entity-backend once it grows
 * an operation layer, implements to supply {@code EntityObject} reads/writes and named
 * {@code TransitionGuard}/{@code TransitionAction} beans).
 */
@ApplicationModule(displayName = "Base State", allowedDependencies = {"core", "shared"})
package com.processpuzzle.state;

import org.springframework.modulith.ApplicationModule;
