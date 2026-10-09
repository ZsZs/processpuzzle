package com.processpuzzle.shared.event;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A raw platform fact: something happened to an object of an entity type. Any feature may publish
 * one — base-entity on create, update and delete, base-state when an object's state changes — and
 * nothing about it says whether anybody cares. Deciding that is base-event's job: its catalog
 * listener matches the fact against the organization's {@code EventDefinition}s and republishes each
 * match as {@link DefinedEventOccurred}, which is what a reacting feature (a workflow start) observes.
 * Subscribing to this event directly means re-implementing that matching.
 *
 * <p>Published inside the writing transaction. Subscribers use
 * {@code @TransactionalEventListener} (after commit — the fact must have happened) together with
 * {@code @Transactional(REQUIRES_NEW)}: after commit the publishing transaction is completed but
 * still bound to the thread, and a {@code REQUIRED} write joins it and is silently discarded.
 *
 * <p>The host application persists every publication in the Spring Modulith event publication
 * registry and redelivers it until each listener completes, so this record — {@link #payload()}
 * included — must stay Jackson-serialisable, and a listener must tolerate seeing the same event
 * twice.
 *
 * @param orgKey      the organization the fact happened in
 * @param subjectType the entity definition code, e.g. {@code order} — what base-state calls
 *                    {@code entityName}
 * @param subjectId   the object's id
 * @param action      what happened
 * @param state       the state entered; {@link PlatformEventAction#STATE_CHANGED} only, null otherwise
 * @param payload     the object's payload after the fact; empty for a delete
 * @param occurredAt  when it happened
 */
public record PlatformEvent(
    String orgKey,
    String subjectType,
    String subjectId,
    PlatformEventAction action,
    String state,
    Map<String, Object> payload,
    Instant occurredAt
) {

    public PlatformEvent {
        // Not Map.copyOf: a payload legitimately holds null values.
        payload = payload == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }

    /** A lifecycle fact — created, updated or deleted. */
    public static PlatformEvent of(String orgKey, String subjectType, String subjectId, PlatformEventAction action,
                                   Map<String, Object> payload, Instant occurredAt) {
        return new PlatformEvent(orgKey, subjectType, subjectId, action, null, payload, occurredAt);
    }

    /** The subject entered {@code state}. */
    public static PlatformEvent stateChanged(String orgKey, String subjectType, String subjectId, String state,
                                             Map<String, Object> payload, Instant occurredAt) {
        return new PlatformEvent(orgKey, subjectType, subjectId, PlatformEventAction.STATE_CHANGED, state, payload,
            occurredAt);
    }
}
