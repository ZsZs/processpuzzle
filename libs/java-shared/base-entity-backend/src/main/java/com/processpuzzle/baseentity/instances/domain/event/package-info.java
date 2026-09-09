/**
 * Domain events the instances module publishes as an {@code EntityObject} is created, updated or
 * deleted. Exposed as the {@code event} named interface, so another module may observe an entity's
 * lifecycle without being allowed at the repository or the aggregate behind it.
 *
 * <p>All three are published <em>after</em> the writing transaction commits, via Spring's
 * {@code @TransactionalEventListener} on the observer side. A listener therefore cannot veto the
 * write it is reacting to — that would need a pre-commit hook, which no consumer needs yet (see
 * base-state's {@code EntityObjectCreatedListener}).
 *
 * <p>base-entity itself stays unaware of who listens. base-state observes {@code
 * EntityObjectCreatedEvent} to start the entity's state machine; nothing here knows that.
 */
@NamedInterface("event")
package com.processpuzzle.baseentity.instances.domain.event;

import org.springframework.modulith.NamedInterface;
