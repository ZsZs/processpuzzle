/**
 * Ports a host application — or, eventually, base-entity-backend's operation layer — implements to
 * feed Base State information it cannot own itself: an {@code EntityObject}'s current payload and
 * version ({@link com.processpuzzle.state.usecase.port.EntityObjectGateway}), and the
 * business-specific guard/action beans a transition names by Spring bean name ({@link
 * com.processpuzzle.state.usecase.port.TransitionGuard}, {@link
 * com.processpuzzle.state.usecase.port.TransitionAction}).
 *
 * <p>Exposed as the {@code port} named interface, separately from {@code usecase}: this is the
 * side of the module other code plugs into, not the side it calls.
 */
@NamedInterface("port")
package com.processpuzzle.state.usecase.port;

import org.springframework.modulith.NamedInterface;
