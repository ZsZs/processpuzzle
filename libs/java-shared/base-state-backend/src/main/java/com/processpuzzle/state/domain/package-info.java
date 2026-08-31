/**
 * Domain model of the ProcessPuzzle base state machine: {@link
 * com.processpuzzle.state.domain.StateMachineDefinition} (one per entity type, JPA-persisted)
 * and the flat, non-parallel, non-nested {@link com.processpuzzle.state.domain.State} /
 * {@link com.processpuzzle.state.domain.Transition} values it declares.
 *
 * <p>Exposed as the {@code domain} named interface — Base Workflow advances workflow instances in
 * response to state changes, so it needs to see states and transitions.
 */
@NamedInterface("domain")
package com.processpuzzle.state.domain;

import org.springframework.modulith.NamedInterface;
