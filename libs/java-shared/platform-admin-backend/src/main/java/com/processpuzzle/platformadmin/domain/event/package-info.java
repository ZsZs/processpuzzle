/**
 * Domain events Platform Admin publishes over a tenant's lifecycle.
 *
 * <p>Exposed as the {@code event} named interface, because reacting to a tenant appearing or
 * disappearing is the intended integration point for every feature that stores organization-scoped
 * data — see {@link com.processpuzzle.platformadmin.domain.event.OrganizationDeletedEvent}. Each
 * event's Javadoc names the {@code @TransactionalEventListener} phase a subscriber must use and why;
 * getting that wrong fails silently rather than loudly.
 */
@NamedInterface("event")
package com.processpuzzle.platformadmin.domain.event;

import org.springframework.modulith.NamedInterface;
