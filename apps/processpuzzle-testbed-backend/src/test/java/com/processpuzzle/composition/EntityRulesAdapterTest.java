package com.processpuzzle.composition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.processpuzzle.rule.domain.Severity;
import com.processpuzzle.rule.usecase.EvaluateObject;
import com.processpuzzle.rule.usecase.EvaluationOutcome;
import com.processpuzzle.rule.usecase.RuleViolation;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EntityRulesAdapterTest {

    private static final String ORG = "org-1";
    private static final Map<String, Object> PAYLOAD = Map.of("total", 120);

    private final EvaluateObject evaluateObject = mock(EvaluateObject.class);
    private final EntityRulesAdapter adapter = new EntityRulesAdapter(evaluateObject);

    @Test
    void reportsNothingWhenOnlyWarningsAreViolated() {
        when(evaluateObject.execute(ORG, "Order", PAYLOAD)).thenReturn(new EvaluationOutcome(true,
                List.of(new RuleViolation("r1", "Address", Severity.WARNING, "Missing shipping address", null))));

        assertThat(adapter.blockingViolationsOf(ORG, "Order", PAYLOAD)).isEmpty();
    }

    @Test
    void reportsTheErrorViolationsMessages() {
        when(evaluateObject.execute(ORG, "Order", PAYLOAD)).thenReturn(new EvaluationOutcome(false, List.of(
                new RuleViolation("r1", "Total", Severity.ERROR, "Total does not match", null),
                new RuleViolation("r2", "Quantities positive", Severity.ERROR, " ", null),
                new RuleViolation("r3", "Address", Severity.WARNING, "Missing shipping address", null))));

        assertThat(adapter.blockingViolationsOf(ORG, "Order", PAYLOAD)).containsExactly("Total does not match", "Quantities positive");
    }

    @Test
    void evaluatesANullPayloadAsAnEmptyOne() {
        when(evaluateObject.execute(ORG, "Order", Map.of())).thenReturn(new EvaluationOutcome(true, List.of()));

        assertThat(adapter.blockingViolationsOf(ORG, "Order", null)).isEmpty();
    }
}
