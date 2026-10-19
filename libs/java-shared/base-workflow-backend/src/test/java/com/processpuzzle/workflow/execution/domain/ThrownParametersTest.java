package com.processpuzzle.workflow.execution.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.processpuzzle.shared.event.EventParameterType;
import com.processpuzzle.workflow.definition.usecases.outbound.CatalogParameter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Decision 1 of docs/event-parameters.md: a thrown parameter is found by name — plain first, then namespaced. */
class ThrownParametersTest {

    private static final List<CatalogParameter> INVOICE_ISSUED = List.of(
            new CatalogParameter("order", EventParameterType.ENTITY_REF, "order", true),
            new CatalogParameter("invoiceNumber", EventParameterType.STRING, null, true),
            new CatalogParameter("note", EventParameterType.STRING, null, false));

    @Test
    void takesThePayloadMappingFirst_thenThePlainVariable() {
        Map<String, Object> filled = ThrownParameters.fill(INVOICE_ISSUED, Map.of("invoiceNumber", "I-mapped"),
                Map.of("order", "o-1", "invoiceNumber", "I-plain"));

        assertThat(filled).containsExactly(Map.entry("order", "o-1"), Map.entry("invoiceNumber", "I-mapped"));
    }

    // The one event that carried it; two would make the name ambiguous, and it is left for a payload mapping.
    @Test
    void fallsBackToTheOneNamespacedValue_andLeavesAnAmbiguousOneOut() {
        Map<String, Object> filled = ThrownParameters.fill(INVOICE_ISSUED, Map.of(), Map.of(
                "invoice-requested", Map.of("order", "o-1", "note", "first"),
                "reminder-sent", Map.of("note", "second")));

        assertThat(filled).containsExactly(Map.entry("order", "o-1"));
    }

    @Test
    void fillsNothingWithoutDeclaredParameters() {
        assertThat(ThrownParameters.fill(null, Map.of("order", "o-1"), Map.of("order", "o-1"))).isEmpty();
    }

    @Test
    void namesTheRequiredParametersNothingFilled() {
        assertThat(ThrownParameters.missing(INVOICE_ISSUED, Map.of("order", "o-1"))).containsExactly("invoiceNumber");
        assertThat(ThrownParameters.missing(INVOICE_ISSUED, Map.of("order", "o-1", "invoiceNumber", "I-1"))).isEmpty();
        assertThat(ThrownParameters.missing(null, Map.of())).isEmpty();
    }
}
