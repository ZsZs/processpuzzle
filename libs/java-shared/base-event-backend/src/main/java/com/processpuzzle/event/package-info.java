/**
 * Base Event: the organization's catalog of named events, and the bridge from raw platform facts to
 * them.
 *
 * <p>A feature that changes something publishes a {@link com.processpuzzle.shared.event.PlatformEvent}
 * — base-entity on create/update/delete, base-state on a state change — without knowing who listens.
 * {@code PlatformEventCatalogListener} matches each one against the organization's SYSTEM
 * {@link com.processpuzzle.event.domain.EventDefinition}s and publishes a
 * {@link com.processpuzzle.shared.event.DefinedEventOccurred} per match, which is what a reacting
 * feature observes: base-workflow starts every workflow whose TRIGGERING_EVENT start event names the
 * definition. Both events live in {@code shared}, so neither side compiles against this module and
 * this module compiles against neither of them.
 *
 * <p>Workflows raise MESSAGE and SIGNAL events the same way: base-workflow publishes an
 * {@link com.processpuzzle.shared.event.EventThrown} when an instance reaches an intermediate throw
 * event, and {@code ThrownEventCatalogListener} republishes it as a {@code DefinedEventOccurred} of
 * that kind — carrying the message's correlation value — once the catalog confirms the definition.
 * So this module is the only publisher of occurrences, whatever raised them.
 *
 * <p>The catalog is not event-sourced and keeps no history: it recognises and republishes. Durability
 * of both hops is the host application's Spring Modulith event publication registry.
 *
 * <p>Exposes {@code event :: usecase}, which the host application reaches through an adapter to
 * answer base-workflow's questions whether an event definition exists and of which kind it is.
 */
@ApplicationModule(
        displayName = "Base Event",
        allowedDependencies = {"core", "shared"})
package com.processpuzzle.event;

import org.springframework.modulith.ApplicationModule;
