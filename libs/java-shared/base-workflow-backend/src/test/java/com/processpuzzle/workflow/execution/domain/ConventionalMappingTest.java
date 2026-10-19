package com.processpuzzle.workflow.execution.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.processpuzzle.shared.event.EventParameterType;
import com.processpuzzle.shared.event.OccurredParameter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Phase 3 of docs/event-parameters.md: a carried parameter lands under its own name unless the mapping names it. */
class ConventionalMappingTest {

    private static final List<OccurredParameter> INVOICE_ISSUED = List.of(
            new OccurredParameter(OccurredParameter.SUBJECT, EventParameterType.ENTITY_REF, "order", "o-1"),
            new OccurredParameter("order", EventParameterType.ENTITY_REF, "order", "o-1"),
            new OccurredParameter("invoiceNumber", EventParameterType.STRING, null, "I-1"),
            new OccurredParameter("note", EventParameterType.STRING, null, null));

    @Test
    void addsEachCarriedParameterByName_butNotTheSubjectNorAnUnfilledOne() {
        assertThat(ConventionalMapping.apply(Map.of("orderId", "o-1"), INVOICE_ISSUED)).containsExactly(
                Map.entry("orderId", "o-1"), Map.entry("order", "o-1"), Map.entry("invoiceNumber", "I-1"));
    }

    // The mapping is the override — also when what it read is nothing.
    @Test
    void leavesANameTheMappingNamesToTheMapping() {
        Map<String, Object> mapped = new HashMap<>();
        mapped.put("invoiceNumber", "I-mapped");
        mapped.put("order", null);

        assertThat(ConventionalMapping.apply(mapped, INVOICE_ISSUED))
                .containsEntry("invoiceNumber", "I-mapped")
                .containsEntry("order", null);
    }

    @Test
    void withoutParametersIsJustTheMapping() {
        assertThat(ConventionalMapping.apply(Map.of("orderId", "o-1"), null)).containsExactly(Map.entry("orderId", "o-1"));
        assertThat(ConventionalMapping.apply(null, List.of())).isEmpty();
    }
}
