package com.processpuzzle.rule.usecase.assist;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class ContextDescriptionTest {

    @Test
    void anEntityIsWrittenOutWithTypesRequirednessMeasuresListsAndNestedAttributes() {
        String text = ContextDescription.of(Optional.of(Orders.context()));

        assertThat(text).contains("Entity: Order")
            .contains("- total: NUMBER, required")
            .contains("- status: ENUM\n")
            .contains("- weight: QUANTITY (kg)")
            .contains("- lineItems: list of embedded object\n")
            .contains("  - quantity: QUANTITY (pc), required")
            .contains("- address: embedded object\n")
            .contains("  - city: TEXT");
    }

    @Test
    void anUnknownEntityIsSaidToBeUnknown() {
        assertThat(ContextDescription.of(Optional.empty())).contains("not known");
    }
}
