/**
 * Domain model of the ProcessPuzzle base state machine.
 *
 * <p>Exposed as the {@code domain} named interface — Base Workflow advances process instances in
 * response to state changes, so it needs to see states and transitions.
 */
@NamedInterface("domain")
package com.processpuzzle.basestate.domain;

import org.springframework.modulith.NamedInterface;
