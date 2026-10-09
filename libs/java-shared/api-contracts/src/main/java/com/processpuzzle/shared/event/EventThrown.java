package com.processpuzzle.shared.event;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A workflow raised a catalogued MESSAGE or SIGNAL event: one of its intermediate throw events was
 * reached. base-event validates it against the organization's catalog and republishes it as
 * {@link DefinedEventOccurred}, so base-event stays the only publisher of occurrences and a catching
 * feature never has to know who threw.
 *
 * <p>Subscribers use {@code @TransactionalEventListener} with {@code @Transactional(REQUIRES_NEW)},
 * for the reason {@link PlatformEvent} gives. The publication registry persists and redelivers this
 * event: keep {@link #payload()} Jackson-serialisable, and make the reaction idempotent —
 * {@link #occurrenceId()} is deterministic for exactly that purpose.
 *
 * @param orgKey                   the organization the workflow runs in
 * @param eventDefinitionId        the catalogued event thrown, e.g. {@code InvoiceRequested}
 * @param occurrenceId             the throwing event instance's id; equal across redeliveries
 * @param subjectType              the throwing instance's entity type — the occurrence is about the
 *                                 same subject as the workflow that threw it
 * @param subjectId                the throwing instance's entity id
 * @param correlationValue         MESSAGE: the value a recipient is matched on; null otherwise
 * @param payload                  what the throw event's payload mapping produced
 * @param sourceWorkflowInstanceId the throwing workflow instance
 * @param sourceWorkflowId         the throwing workflow's definition id
 * @param occurredAt               when it was thrown
 */
public record EventThrown(
    String orgKey,
    String eventDefinitionId,
    UUID occurrenceId,
    String subjectType,
    String subjectId,
    String correlationValue,
    Map<String, Object> payload,
    UUID sourceWorkflowInstanceId,
    String sourceWorkflowId,
    Instant occurredAt
) {

    public EventThrown {
        payload = payload == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }
}
