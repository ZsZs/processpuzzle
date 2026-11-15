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
        assertThat(item).containsEntry("number", 1).containsEntry("itemVersion", 1);
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
        assertThat(item).containsEntry("id", "r1").containsEntry("itemVersion", 1);
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

    @Test
    void itemsNestedInItemsGetIdentityAtEveryLevel() {
        EntityDefinitionView round = new EntityDefinitionView("Round", true, List.of(
            new EntityAttributeView("number", ValueKindView.NUMBER, false, false, null, false),
            new EntityAttributeView("results", ValueKindView.TEXT, true, true, "Result", false)));
        EntityDefinitionView result = new EntityDefinitionView("Result", true, List.of(new EntityAttributeView("score", ValueKindView.NUMBER, false, false, null, false)));
        EntityDefinitionView race = new EntityDefinitionView("race", false, List.of(new EntityAttributeView("rounds", ValueKindView.TEXT, true, true, "Round", false)));
        Map<String, Object> payload = Map.of("rounds", List.of(Map.of("number", 1, "results", List.of(Map.of("score", 5)))));

        Map<String, Object> result2 = EmbeddedItemIdentity.withIdentities(race, payload, code -> java.util.Optional.of(
            code.equals("Round") ? round : result));

        @SuppressWarnings("unchecked")
        Map<String, Object> roundItem = ((List<Map<String, Object>>) result2.get("rounds")).get(0);
        assertThat(roundItem.get("id")).isNotNull();
        assertThat(roundItem).containsEntry("itemVersion", 1);
        @SuppressWarnings("unchecked")
        Map<String, Object> resultItem = ((List<Map<String, Object>>) roundItem.get("results")).get(0);
        assertThat(resultItem.get("id")).isNotNull();
        assertThat(resultItem).containsEntry("itemVersion", 1).containsEntry("score", 5);
    }

    @Test
    void aNestedLevelThatIsAlreadyIdentifiedIsKeptAsItIs() {
        EntityDefinitionView round = new EntityDefinitionView("Round", true, List.of(new EntityAttributeView("results", ValueKindView.TEXT, true, true, "Result", false)));
        EntityDefinitionView race = new EntityDefinitionView("race", false, List.of(new EntityAttributeView("rounds", ValueKindView.TEXT, true, true, "Round", false)));
        Map<String, Object> payload = Map.of("rounds", List.of(Map.of("id", "r1", "itemVersion", 2, "results", List.of())));

        assertThat(EmbeddedItemIdentity.withIdentities(race, payload, code -> java.util.Optional.of(round))).isSameAs(payload);
    }
}
