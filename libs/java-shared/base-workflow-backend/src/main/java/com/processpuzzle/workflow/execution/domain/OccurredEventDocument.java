package com.processpuzzle.workflow.execution.domain;

import com.processpuzzle.shared.event.DefinedEventOccurred;
import com.processpuzzle.shared.event.PlatformEvent;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A {@link DefinedEventOccurred} as the document a {@link PayloadPath} is evaluated against — by a
 * TRIGGERING_EVENT start event's {@code payloadMapping} and by a catch event's. So {@code $.subjectId},
 * {@code $.correlationValue} or {@code $.payload.invoiceNumber}.
 */
public final class OccurredEventDocument {

    private OccurredEventDocument() {
    }

    public static Map<String, Object> of(DefinedEventOccurred event) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("orgKey", event.orgKey());
        document.put("eventDefinitionId", event.eventDefinitionId());
        document.put("kind", event.effectiveKind().name());
        document.put("occurrenceId", event.occurrenceId() == null ? null : event.occurrenceId().toString());
        document.put("subjectType", event.subjectType());
        document.put("subjectId", event.subjectId());
        document.put("correlationValue", event.correlationValue());
        document.put("sourceWorkflowInstanceId",
                event.sourceWorkflowInstanceId() == null ? null : event.sourceWorkflowInstanceId().toString());
        document.put("payload", event.payload());
        document.put("occurredAt", event.occurredAt() == null ? null : event.occurredAt().toString());
        return document;
    }

    /** A platform fact as an INPUT_ARTIFACT start event's {@code payloadMapping} reads it. */
    public static Map<String, Object> of(PlatformEvent event) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("orgKey", event.orgKey());
        document.put("subjectType", event.subjectType());
        document.put("subjectId", event.subjectId());
        document.put("action", event.action() == null ? null : event.action().name());
        document.put("state", event.state());
        document.put("payload", event.payload());
        document.put("occurredAt", event.occurredAt() == null ? null : event.occurredAt().toString());
        return document;
    }
}
