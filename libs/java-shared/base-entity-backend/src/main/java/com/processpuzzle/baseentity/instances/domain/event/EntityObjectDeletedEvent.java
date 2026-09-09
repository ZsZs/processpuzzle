package com.processpuzzle.baseentity.instances.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published post-commit when an {@code EntityObject} has been deleted.
 *
 * <p>No payload: the object is gone by the time an observer runs, and an observer that needs the
 * values it held should be keeping its own projection rather than reading a corpse. Nothing in
 * base-state reacts to this — an object's state lives in its own payload and is deleted with it —
 * but a module that keeps state <em>beside</em> the object (base-workflow's workflow instances) has
 * to learn that its subject no longer exists.
 *
 * @param orgKey               the organization the deleting request was addressed to — see
 *                             {@link EntityObjectCreatedEvent#orgKey()}
 * @param entityDefinitionCode the entity type's code — what base-state calls {@code entityName}
 * @param objectId             the deleted object's id
 * @param occurredAt           when the delete committed
 */
public record EntityObjectDeletedEvent(
    String orgKey,
    String entityDefinitionCode,
    UUID objectId,
    Instant occurredAt
) {
}
