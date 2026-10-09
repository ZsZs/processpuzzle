package com.processpuzzle.composition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.processpuzzle.rule.domain.Severity;
import com.processpuzzle.rule.usecase.EvaluateObject;
import com.processpuzzle.rule.usecase.EvaluationOutcome;
import com.processpuzzle.rule.usecase.RuleViolation;
import com.processpuzzle.state.usecase.port.GuardResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StateRuleAdapterTest {

    private static final String ORG = "org-1";
    private static final Map<String, Object> PAYLOAD = Map.of("amount", 120);

    private final EvaluateObject evaluateObject = mock(EvaluateObject.class);
    private final StateRuleAdapter adapter = new StateRuleAdapter(evaluateObject);

    @Test
    void allowsWhenTheRulesPass() {
        when(evaluateObject.execute(ORG, "order-approval", PAYLOAD)).thenReturn(new EvaluationOutcome(true,
                List.of(new RuleViolation("r1", "Advice", Severity.WARNING, "Consider a discount", null))));

        assertThat(adapter.evaluate(ORG, "order-approval", PAYLOAD).isAllowed()).isTrue();
    }

    @Test
    void rejectsWithTheErrorViolationsAsReason() {
        when(evaluateObject.execute(ORG, "order-approval", PAYLOAD)).thenReturn(new EvaluationOutcome(false, List.of(
                new RuleViolation("r1", "Limit", Severity.ERROR, "Amount over limit", null),
                new RuleViolation("r2", "Customer required", Severity.ERROR, " ", null),
                new RuleViolation("r3", "Advice", Severity.WARNING, "Consider a discount", null))));

        GuardResult result = adapter.evaluate(ORG, "order-approval", PAYLOAD);

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.reason()).isEqualTo("Amount over limit; Customer required");
    }

    @Test
    void evaluatesANullSubjectAsAnEmptyPayload() {
        when(evaluateObject.execute(ORG, "order-approval", Map.of())).thenReturn(new EvaluationOutcome(true, List.of()));

        assertThat(adapter.evaluate(ORG, "order-approval", null).isAllowed()).isTrue();
    }
}
