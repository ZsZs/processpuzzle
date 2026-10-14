package com.processpuzzle.core.definition;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MigrationStepTest {

    @Test
    void aStepWithoutParametersHasNone_ratherThanNull() {
        MigrationStep step = new MigrationStep("s1", DefinitionKinds.ENTITY, "claim", "fillAttribute", "currency", null);

        assertThat(step.parameters()).isEmpty();
    }

    /** A step is a value: changing the map it was made from does not change it, and its own map cannot be changed. */
    @Test
    void keepsACopyOfItsParameters() {
        Map<String, Object> parameters = new HashMap<>(Map.of("to", "name"));
        MigrationStep step = new MigrationStep("s1", DefinitionKinds.ENTITY, "customer", "renameAttribute", "customerName",
            parameters);

        parameters.put("to", "fullName");

        assertThat(step.parameters()).containsExactly(Map.entry("to", "name"));
        assertThatThrownBy(() -> step.parameters().put("to", "other")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void aFailedStepSaysWhy() {
        assertThat(new MigrationStepFailedException("2 object(s) do not fit.")).hasMessage("2 object(s) do not fit.");
    }
}
