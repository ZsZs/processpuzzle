package com.processpuzzle.shared.event;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A {@link PlatformEvent} that the organization's event catalog recognised: base-event found an
 * {@code EventDefinition} whose binding matches the fact, and publishes one of these per matching
 * definition. It is the event a reacting feature subscribes to — base-workflow starts every workflow
 * whose TRIGGERING_EVENT start event names {@link #eventDefinitionId()} — so that feature needs to
 * know event definitions by id only, and nothing about which raw fact produced them.
 *
 * <p>Subscribers use {@code @TransactionalEventListener} with {@code @Transactional(REQUIRES_NEW)},
 * for the reason {@link PlatformEvent} gives. The publication registry persists and redelivers this
 * event too: keep {@link #payload()} Jackson-serialisable, and make the reaction idempotent.
 *
 * @param orgKey            the organization the fact happened in
 * @param eventDefinitionId the matching {@code EventDefinition}'s id, e.g. {@code OrderCreatedEvent}
 * @param subjectType       the entity definition code of the subject
 * @param subjectId         the subject object's id
 * @param payload           the subject's payload as the raw fact carried it
 * @param occurredAt        when the raw fact happened
 */
public record DefinedEventOccurred(
    String orgKey,
    String eventDefinitionId,
    String subjectType,
    String subjectId,
    Map<String, Object> payload,
    Instant occurredAt
) {

    public DefinedEventOccurred {
        payload = payload == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }
}
