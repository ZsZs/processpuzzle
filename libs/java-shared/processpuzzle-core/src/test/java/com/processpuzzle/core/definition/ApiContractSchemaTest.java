package com.processpuzzle.core.definition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ApiContractSchemaTest {

    private static final String FIXTURE = "api-contract-schema-fixture.yaml";

    @Test
    void inlinesEveryReferenceOfTheComponent() {
        String schema = ApiContractSchema.ofComponent(FIXTURE, "Widget");

        assertThat(schema)
            .contains("\"enum\":[\"small\",\"large\"]", "\"name\":{\"type\":\"string\"}")
            .doesNotContain("$ref");
    }

    @Test
    void keepsTheRequiredFieldsOfTheComponent() {
        assertThat(ApiContractSchema.ofComponent(FIXTURE, "Widget")).contains("\"required\":[\"code\",\"parts\"]");
    }

    @Test
    void aMissingComponentIsAnError() {
        assertThatThrownBy(() -> ApiContractSchema.ofComponent(FIXTURE, "Gadget"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("no component Gadget");
    }

    @Test
    void aMissingContractIsAnError() {
        assertThatThrownBy(() -> ApiContractSchema.ofComponent("absent.yaml", "Widget"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("is not on the classpath");
    }
}
