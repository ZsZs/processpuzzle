package com.processpuzzle.baseentity.instances.domain.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Published post-commit when an {@code EntityObject} has been created.
 *
 * <p>Carries the whole payload rather than only the object's identity, so an observer that has to
 * decide something from the created values — base-state asks whether the state attribute was already
 * supplied — does not have to read the object back and race with a concurrent update.
 *
 * @param orgKey               the organization the creating request was addressed to. Taken from the
 *                             request path: {@code EntityObject} itself carries no organization
 *                             column, so this is the only place the tenant is known, and it is what
 *                             scopes the metadata an observer resolves (a state machine, a rule).
 * @param entityDefinitionCode the entity type's code — what base-state calls {@code entityName}
 * @param objectId             the new object's id
 * @param payload              the payload as persisted
 * @param version              the object's optimistic-lock version after the insert
 * @param occurredAt           when the object was created
 */
public record EntityObjectCreatedEvent(
    String orgKey,
    String entityDefinitionCode,
    UUID objectId,
    Map<String, Object> payload,
    long version,
    Instant occurredAt
) {

    public EntityObjectCreatedEvent {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
