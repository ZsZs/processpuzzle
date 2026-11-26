package com.processpuzzle.core.assist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AssistRequestTest {

    @Test
    void aMissingParameterMapAndCurrentValueAreEmptyNotNull() {
        AssistRequest request = new AssistRequest("describe it", null, null);

        assertThat(request.parameters()).isEmpty();
        assertThat(request.current()).isEmpty();
        assertThat(request.text()).isEqualTo("describe it");
    }

    @Test
    void theGivenValuesAreKept() {
        AssistRequest request = new AssistRequest("describe it", Map.of("context", "Order"), "entity.total > 0");

        assertThat(request.parameters()).containsEntry("context", "Order");
        assertThat(request.current()).isEqualTo("entity.total > 0");
    }

    @Test
    void theParametersAreACopyThatCannotBeChangedByTheCaller() {
        Map<String, String> source = new HashMap<>();
        source.put("context", "Order");
        AssistRequest request = new AssistRequest("describe it", source, "");

        source.put("context", "Changed");

        assertThat(request.parameters()).containsEntry("context", "Order");
        assertThatThrownBy(() -> request.parameters().put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
    }
}
