package com.processpuzzle.baseentity.instances.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.processpuzzle.baseentity.instances.domain.RecordPlanner.RecordInput;
import com.processpuzzle.baseentity.instances.domain.RecordPlanner.Ref;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RecordPlannerTest {

    private static Map<String, Object> ref(String entity, String attribute, Object value) {
        return Map.of(RecordPlanner.REF, Map.of("entity", entity, "key", Map.of(attribute, value)));
    }

    @Test
    void orders_a_record_after_the_record_it_refers_to() {
        List<RecordInput> batch = List.of(
                new RecordInput("boat", Map.of("sailNumber", "G 470", "boatClass", ref("boat-class", "name", "470"))),
                new RecordInput("boat-class", Map.of("name", "470")));

        RecordPlanner.Ordering ordering = RecordPlanner.order(batch);

        assertThat(ordering.order()).containsExactly(1, 0);
        assertThat(ordering.cannotLoad()).isEmpty();
    }

    @Test
    void keeps_the_file_order_where_nothing_depends_on_anything() {
        List<RecordInput> batch = List.of(
                new RecordInput("boat-class", Map.of("name", "Star")),
                new RecordInput("boat-class", Map.of("name", "Finn")));

        assertThat(RecordPlanner.order(batch).order()).containsExactly(0, 1);
    }

    @Test
    void refuses_a_reference_cycle_and_keeps_every_record_in_the_order() {
        List<RecordInput> batch = List.of(
                new RecordInput("a", Map.of("name", "x", "link", ref("b", "name", "y"))),
                new RecordInput("b", Map.of("name", "y", "link", ref("a", "name", "x"))),
                new RecordInput("c", Map.of("name", "z")));

        RecordPlanner.Ordering ordering = RecordPlanner.order(batch);

        assertThat(ordering.order()).containsExactlyInAnyOrder(0, 1, 2);
        assertThat(ordering.cannotLoad()).containsOnlyKeys(0, 1);
        assertThat(ordering.cannotLoad().get(0)).contains("cycle");
    }

    @Test
    void refuses_a_malformed_reference_instead_of_storing_it_as_text() {
        List<RecordInput> batch = List.of(
                new RecordInput("boat", Map.of("boatClass", Map.of(RecordPlanner.REF, Map.of("entity", "boat-class")))));

        RecordPlanner.Ordering ordering = RecordPlanner.order(batch);

        assertThat(ordering.cannotLoad()).containsKey(0);
        assertThat(ordering.cannotLoad().get(0)).contains("names no key");
    }

    @Test
    void reads_the_references_of_a_list_attribute() {
        Map<String, Object> payload = Map.of("crew", List.of(ref("sailor", "lastName", "Doe"), ref("sailor", "lastName", "Roe")));

        assertThat(RecordPlanner.refsOf(payload))
                .extracting(Ref::entity)
                .containsExactly("sailor", "sailor");
    }

    @Test
    void resolves_references_and_keeps_other_values() {
        Map<String, Object> payload = Map.of("name", "470", "boatClass", ref("boat-class", "name", "470"));

        Map<String, Object> resolved = RecordPlanner.resolve(payload, ref -> "id-of-" + ref.key().get("name"));

        assertThat(resolved).containsEntry("name", "470").containsEntry("boatClass", "id-of-470");
    }

    @Test
    void a_key_with_a_missing_value_does_not_match_or_clash() {
        Map<String, Object> stored = Map.of("name", "470");
        Map<String, Object> incoming = Map.of("description", "no name");

        assertThat(RecordPlanner.sameKey(stored, incoming, List.of("name"))).isFalse();
        assertThat(RecordPlanner.sameKey(stored, Map.of("name", "470"), List.of("name"))).isTrue();
    }

    @Test
    void a_reference_without_a_key_is_refused() {
        assertThatThrownBy(() -> RecordPlanner.refsOf(Map.of("x", Map.of(RecordPlanner.REF, Map.of("entity", "a", "key", Map.of())))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
