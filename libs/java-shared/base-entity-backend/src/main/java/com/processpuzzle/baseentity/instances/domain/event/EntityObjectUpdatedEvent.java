package com.processpuzzle.baseentity.instances.domain.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Published post-commit when an {@code EntityObject}'s payload has been replaced.
 *
 * <p>Fired by <em>every</em> update, base-state's own write of the state attribute included — so an
 * observer that reacts to this by writing the object again recurses. That is why base-state observes
 * only {@link EntityObjectCreatedEvent}; see its {@code EntityObjectCreatedListener}.
 *
 * @param orgKey               the organization the updating request was addressed to — see
 *                             {@link EntityObjectCreatedEvent#orgKey()}
 * @param entityDefinitionCode the entity type's code — what base-state calls {@code entityName}
 * @param objectId             the object's id
 * @param payload              the payload as persisted by this update
 * @param version              the object's optimistic-lock version after the update
 * @param occurredAt           when the update committed
 */
public record EntityObjectUpdatedEvent(
    String orgKey,
    String entityDefinitionCode,
    UUID objectId,
    Map<String, Object> payload,
    long version,
    Instant occurredAt
) {

    public EntityObjectUpdatedEvent {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
