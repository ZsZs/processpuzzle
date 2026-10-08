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
 * <p>The catalog is not event-sourced and keeps no history: it recognises and republishes. Durability
 * of both hops is the host application's Spring Modulith event publication registry.
 *
 * <p>Exposes {@code event :: usecase}, which the host application reaches through an adapter to
 * answer base-workflow's question whether an event definition exists.
 */
@ApplicationModule(
        displayName = "Base Event",
        allowedDependencies = {"core", "shared"})
package com.processpuzzle.event;

import org.springframework.modulith.ApplicationModule;
