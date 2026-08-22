/**
 * The surface base-entity exposes to other Spring Modulith application modules — base-state in
 * particular. Everything else in base-entity stays internal to the module, so a caller cannot reach
 * {@code EntityObjectRepository} or the {@code BaseEntityDefinition} aggregate directly.
 *
 * <p>Mirrors base-state's own {@code state.api} / {@code state :: operations} arrangement: an
 * in-process equivalent of the REST operation layer for callers deployed in the same application,
 * avoiding an HTTP round trip for what is really a method call within the monolith.
 *
 * <p>Two interfaces, because the two callers need different things: {@link
 * com.processpuzzle.baseentity.api.EntityAttributeQuery} answers questions about an entity
 * <em>type</em> (base-state validates that a state machine's {@code stateAttributeKey} names a real
 * attribute of a usable kind), and {@link com.processpuzzle.baseentity.api.EntityObjectAccess}
 * reads and writes a single <em>instance</em> (base-state's {@code EntityObjectGateway} adapter).
 */
@NamedInterface("operations")
package com.processpuzzle.baseentity.api;

import org.springframework.modulith.NamedInterface;
