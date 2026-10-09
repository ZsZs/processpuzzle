package com.processpuzzle.workflow.execution.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PayloadPathTest {

    private static final Map<String, Object> EVENT = Map.of(
            "subjectId", "42",
            "payload", Map.of(
                    "customerName", "ACME",
                    "lines", List.of(Map.of("sku", "A-1"), Map.of("sku", "B-2"))));

    @Test
    void resolvesTheRootANestedPropertyAndAListElement() {
        assertThat(PayloadPath.resolve(EVENT, "$")).isSameAs(EVENT);
        assertThat(PayloadPath.resolve(EVENT, "$.subjectId")).isEqualTo("42");
        assertThat(PayloadPath.resolve(EVENT, "$.payload.customerName")).isEqualTo("ACME");
        assertThat(PayloadPath.resolve(EVENT, "$.payload.lines.1.sku")).isEqualTo("B-2");
    }

    @Test
    void aPathThatDoesNotResolveIsNull() {
        assertThat(PayloadPath.resolve(EVENT, "$.payload.missing")).isNull();
        assertThat(PayloadPath.resolve(EVENT, "$.payload.lines.9.sku")).isNull();
        assertThat(PayloadPath.resolve(EVENT, "$.payload.lines.first")).isNull();
        assertThat(PayloadPath.resolve(EVENT, "$.subjectId.length")).isNull();
    }

    @Test
    void rejectsAnUnsupportedPath() {
        assertThatThrownBy(() -> PayloadPath.resolve(EVENT, "subjectId")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PayloadPath.resolve(EVENT, "$..sku")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PayloadPath.resolve(EVENT, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mapsEveryVariableAndKeepsUnresolvedOnesAsNull() {
        Map<String, String> mapping = new LinkedHashMap<>();
        mapping.put("orderId", "$.subjectId");
        mapping.put("nickname", "$.payload.nickname");

        assertThat(PayloadPath.map(EVENT, mapping))
                .containsEntry("orderId", "42")
                .containsEntry("nickname", null)
                .hasSize(2);
        assertThat(PayloadPath.map(EVENT, null)).isEmpty();
    }
}
