package com.processpuzzle.baseentity.instances.domain.event;

import com.processpuzzle.baseentity.api.EntityObjectView;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A payload may hold null values — an attribute a form left empty is posted as {@code null}. Until
 * 2026-10-04 the events and the view copied it with {@code Map.copyOf}, which throws on them, so saving
 * such an object failed with a 500 after it had already been written.
 */
class EntityObjectEventPayloadTest {

    private static Map<String, Object> payloadWithAnEmptyAttribute() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("race", "r-1");
        payload.put("helm", null);
        return payload;
    }

    @Test
    void theCreatedEventKeepsNullValuesAndIsUnmodifiable() {
        var event = new EntityObjectCreatedEvent("org", "race-registration", UUID.randomUUID(), payloadWithAnEmptyAttribute(), 0, Instant.now());

        assertThat(event.payload()).containsEntry("race", "r-1").containsEntry("helm", null);
        assertThatThrownBy(() -> event.payload().put("x", 1)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void theUpdatedEventKeepsNullValues() {
        var event = new EntityObjectUpdatedEvent("org", "race-registration", UUID.randomUUID(), payloadWithAnEmptyAttribute(), 1, Instant.now());

        assertThat(event.payload()).containsEntry("helm", null);
    }

    @Test
    void theViewKeepsNullValuesAndCopiesDefensively() {
        Map<String, Object> source = payloadWithAnEmptyAttribute();
        var view = new EntityObjectView(UUID.randomUUID(), 0, source);
        source.put("race", "r-2");

        assertThat(view.payload()).containsEntry("race", "r-1").containsEntry("helm", null);
    }

    @Test
    void aMissingPayloadIsEmpty() {
        assertThat(new EntityObjectCreatedEvent("org", "x", UUID.randomUUID(), null, 0, Instant.now()).payload()).isEmpty();
    }
}
