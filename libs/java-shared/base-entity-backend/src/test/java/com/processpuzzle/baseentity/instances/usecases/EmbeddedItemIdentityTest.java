package com.processpuzzle.baseentity.instances.usecases;

import com.processpuzzle.baseentity.instances.usecases.inbound.EmbeddedItemIdentity;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityAttributeView;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityAttributeView.ValueKindView;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddedItemIdentityTest {

    private final EntityDefinitionView race = new EntityDefinitionView("race", false, List.of(
        new EntityAttributeView("name", ValueKindView.TEXT, false, false, null, true),
        new EntityAttributeView("rounds", ValueKindView.TEXT, true, true, "Round", false),
        new EntityAttributeView("field", ValueKindView.TEXT, false, true, "Field", false)));

    @Test
    void anItemWithoutIdentityGetsAnIdAndTheFirstVersion() {
        Map<String, Object> result = EmbeddedItemIdentity.withIdentities(race, Map.of("rounds", List.of(Map.of("number", 1))));

        @SuppressWarnings("unchecked")
        Map<String, Object> item = ((List<Map<String, Object>>) result.get("rounds")).get(0);
        assertThat(item).containsEntry("number", 1).containsEntry("itemVersion", 1L);
        assertThat(item.get("id")).asString().isNotBlank();
    }

    @Test
    void anItemThatHasIdentityIsKeptAsItIs() {
        Map<String, Object> payload = Map.of("rounds", List.of(Map.of("id", "r1", "itemVersion", 4L)));

        assertThat(EmbeddedItemIdentity.withIdentities(race, payload)).isSameAs(payload);
    }

    @Test
    void aMissingVersionIsFilledWithoutTouchingAnExistingId() {
        Map<String, Object> result = EmbeddedItemIdentity.withIdentities(race, Map.of("rounds", List.of(Map.of("id", "r1"))));

        @SuppressWarnings("unchecked")
        Map<String, Object> item = ((List<Map<String, Object>>) result.get("rounds")).get(0);
        assertThat(item).containsEntry("id", "r1").containsEntry("itemVersion", 1L);
    }

    @Test
    void theInputIsNotChanged() {
        List<Object> items = new ArrayList<>(List.of(Map.of("number", 1)));
        Map<String, Object> payload = Map.of("rounds", items);

        EmbeddedItemIdentity.withIdentities(race, payload);

        assertThat(items.get(0)).isEqualTo(Map.of("number", 1));
    }

    @Test
    void singleEmbeddedObjectsAndOtherAttributesAreNotTouched() {
        Map<String, Object> payload = Map.of("field", Map.of("number", 1), "name", "Spring Cup");

        assertThat(EmbeddedItemIdentity.withIdentities(race, payload)).isSameAs(payload);
    }

    @Test
    void noDefinitionOrNoPayloadMeansNothingToDo() {
        assertThat(EmbeddedItemIdentity.withIdentities(null, Map.of("rounds", List.of(Map.of()))).get("rounds")).isEqualTo(List.of(Map.of()));
        assertThat(EmbeddedItemIdentity.withIdentities(race, null)).isNull();
    }
}
