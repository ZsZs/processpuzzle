package com.processpuzzle.shared.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * An event of the organization's catalog occurred. base-event publishes one per matching definition
 * when a {@link PlatformEvent} matches a SYSTEM definition's binding, and one when a workflow's
 * {@link EventThrown} names a MESSAGE or SIGNAL definition. It is the event a reacting feature
 * subscribes to — base-workflow starts every workflow whose TRIGGERING_EVENT start event names
 * {@link #eventDefinitionId()}, and delivers it to the intermediate catch events waiting for it — so
 * that feature needs to know event definitions by id only, and nothing about what produced them.
 *
 * <p>Subscribers use {@code @TransactionalEventListener} with {@code @Transactional(REQUIRES_NEW)},
 * for the reason {@link PlatformEvent} gives. The publication registry persists and redelivers this
 * event too: keep {@link #payload()} Jackson-serialisable, and make the reaction idempotent.
 *
 * <p>Publications stored before {@link #kind()} existed deserialise with a null kind; read it through
 * {@link #effectiveKind()}, which treats that as SYSTEM.
 *
 * @param orgKey                   the organization the event occurred in
 * @param eventDefinitionId        the catalogued event's id, e.g. {@code OrderCreatedEvent}
 * @param subjectType              the entity definition code of the subject
 * @param subjectId                the subject object's id
 * @param payload                  SYSTEM: the subject's payload as the raw fact carried it;
 *                                 MESSAGE / SIGNAL: what the throwing workflow sent
 * @param occurredAt               when it occurred
 * @param kind                     how it is delivered; null on publications older than this field
 * @param occurrenceId             identifies this occurrence across redeliveries
 * @param correlationValue         MESSAGE: the value the recipient is matched on; null otherwise
 * @param sourceWorkflowInstanceId MESSAGE / SIGNAL: the workflow instance that threw it
 * @param parameters               the definition's parameters, resolved against this occurrence — SYSTEM: the
 *                                 implicit {@link OccurredParameter#SUBJECT} and the declared ones from the
 *                                 payload; MESSAGE / SIGNAL: the declared ones from what the thrower sent. Empty
 *                                 on publications older than this field
 */
public record DefinedEventOccurred(
    String orgKey,
    String eventDefinitionId,
    String subjectType,
    String subjectId,
    Map<String, Object> payload,
    Instant occurredAt,
    CatalogEventKind kind,
    UUID occurrenceId,
    String correlationValue,
    UUID sourceWorkflowInstanceId,
    List<OccurredParameter> parameters
) {

    @JsonCreator
    public DefinedEventOccurred {
        payload = payload == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
        // Not List.copyOf: a parameter the occurrence did not carry legitimately holds a null value, and the list
        // itself is null on a publication older than this field.
        parameters = parameters == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(parameters));
    }

    /** An occurrence without resolved parameters — what was published before parameters existed. */
    public DefinedEventOccurred(String orgKey, String eventDefinitionId, String subjectType, String subjectId,
                                Map<String, Object> payload, Instant occurredAt, CatalogEventKind kind, UUID occurrenceId,
                                String correlationValue, UUID sourceWorkflowInstanceId) {
        this(orgKey, eventDefinitionId, subjectType, subjectId, payload, occurredAt, kind, occurrenceId, correlationValue,
            sourceWorkflowInstanceId, List.of());
    }

    /**
     * A SYSTEM occurrence. Its occurrence id is derived from the fact, so that the same fact
     * republished for the same definition yields the same id.
     */
    public DefinedEventOccurred(String orgKey, String eventDefinitionId, String subjectType, String subjectId,
                                Map<String, Object> payload, Instant occurredAt) {
        this(orgKey, eventDefinitionId, subjectType, subjectId, payload, occurredAt, List.of());
    }

    /** A SYSTEM occurrence with its parameters resolved. */
    public DefinedEventOccurred(String orgKey, String eventDefinitionId, String subjectType, String subjectId,
                                Map<String, Object> payload, Instant occurredAt, List<OccurredParameter> parameters) {
        this(orgKey, eventDefinitionId, subjectType, subjectId, payload, occurredAt, CatalogEventKind.SYSTEM,
            systemOccurrenceId(orgKey, eventDefinitionId, subjectType, subjectId, occurredAt), null, null, parameters);
    }

    /** The parameter of that name, or null when the occurrence carries none. */
    public OccurredParameter parameter(String name) {
        return parameters.stream().filter(parameter -> parameter.name().equals(name)).findFirst().orElse(null);
    }

    /** {@link #kind()}, with a publication older than that field read as SYSTEM. */
    public CatalogEventKind effectiveKind() {
        return kind == null ? CatalogEventKind.SYSTEM : kind;
    }

    private static UUID systemOccurrenceId(String orgKey, String eventDefinitionId, String subjectType,
                                           String subjectId, Instant occurredAt) {
        String key = String.join("|", String.valueOf(orgKey), String.valueOf(eventDefinitionId),
            String.valueOf(subjectType), String.valueOf(subjectId), String.valueOf(occurredAt));
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }
}
