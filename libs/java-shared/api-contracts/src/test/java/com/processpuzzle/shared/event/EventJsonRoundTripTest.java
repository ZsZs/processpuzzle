package com.processpuzzle.shared.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * The event publication registry stores these events as JSON and redelivers them from it, so each has
 * to survive a round trip — and a {@link DefinedEventOccurred} stored before it had a kind has to
 * deserialise still.
 */
class EventJsonRoundTripTest {

    private static final Instant WHEN = Instant.parse("2026-10-08T10:00:00Z");
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void eventThrownRoundTrips() {
        EventThrown thrown = new EventThrown("org-1", "InvoiceRequested", UUID.randomUUID(), "order", "42", "42",
                Map.of("orderNumber", "O-1"), UUID.randomUUID(), "order-fulfillment-workflow", WHEN);

        assertThat(mapper.readValue(mapper.writeValueAsString(thrown), EventThrown.class)).isEqualTo(thrown);
    }

    @Test
    void definedEventOccurredRoundTrips() {
        DefinedEventOccurred occurred = new DefinedEventOccurred("org-1", "InvoiceIssued", "order", "42",
                Map.of("invoiceNumber", "I-1"), WHEN, CatalogEventKind.MESSAGE, UUID.randomUUID(), "42",
                UUID.randomUUID());

        assertThat(mapper.readValue(mapper.writeValueAsString(occurred), DefinedEventOccurred.class))
                .isEqualTo(occurred);
    }

    @Test
    void aPublicationStoredBeforeKindExistedReadsAsSystem() {
        String legacy = """
                {"orgKey":"org-1","eventDefinitionId":"OrderCreatedEvent","subjectType":"order",
                 "subjectId":"42","payload":{"customer":"ACME"},"occurredAt":"2026-10-08T10:00:00Z"}""";

        DefinedEventOccurred occurred = mapper.readValue(legacy, DefinedEventOccurred.class);

        assertThat(occurred.kind()).isNull();
        assertThat(occurred.effectiveKind()).isEqualTo(CatalogEventKind.SYSTEM);
        assertThat(occurred.payload()).containsEntry("customer", "ACME");
    }

    @Test
    void theSystemConstructorDerivesADeterministicOccurrenceId() {
        DefinedEventOccurred first = new DefinedEventOccurred("org-1", "E", "order", "42", null, WHEN);
        DefinedEventOccurred again = new DefinedEventOccurred("org-1", "E", "order", "42", Map.of(), WHEN);
        DefinedEventOccurred other = new DefinedEventOccurred("org-1", "E", "order", "43", null, WHEN);

        assertThat(first.kind()).isEqualTo(CatalogEventKind.SYSTEM);
        assertThat(first.occurrenceId()).isEqualTo(again.occurrenceId()).isNotEqualTo(other.occurrenceId());
    }
}
