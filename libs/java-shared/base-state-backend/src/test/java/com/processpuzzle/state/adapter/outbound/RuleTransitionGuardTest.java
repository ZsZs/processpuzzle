package com.processpuzzle.state.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.processpuzzle.state.usecase.port.EntityObjectSnapshot;
import com.processpuzzle.state.usecase.port.GuardResult;
import com.processpuzzle.state.usecase.port.RuleEvaluationPort;
import com.processpuzzle.state.usecase.port.TransitionContext;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class RuleTransitionGuardTest {

    private static final String ORG = "acme";
    private static final UUID OBJECT_ID = UUID.randomUUID();
    private static final Map<String, Object> PAYLOAD = Map.of("amount", 120);

    private final RuleEvaluationPort rules = mock(RuleEvaluationPort.class);
    private final RuleTransitionGuard guard = new RuleTransitionGuard(rules);

    @Test
    void evaluatesTheNamedRuleContextAgainstThePayload() {
        when(rules.evaluate(ORG, "order-approval", PAYLOAD)).thenReturn(GuardResult.rejected("Amount over limit"));

        GuardResult result = guard.evaluate(contextWith(Map.of(RuleTransitionGuard.CONTEXT_PARAM, "order-approval")));

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.reason()).isEqualTo("Amount over limit");
    }

    @Test
    void rejectsWhenNoRuleContextIsNamed() {
        assertThat(guard.evaluate(contextWith(Map.of())).isAllowed()).isFalse();
        assertThat(guard.evaluate(contextWith(null)).reason()).contains(RuleTransitionGuard.CONTEXT_PARAM);
        assertThat(guard.evaluate(contextWith(Map.of(RuleTransitionGuard.CONTEXT_PARAM, " "))).isAllowed()).isFalse();
        verifyNoInteractions(rules);
    }

    @Test
    void passesEverythingWithoutARuleEngine() {
        @SuppressWarnings("unchecked")
        ObjectProvider<RuleEvaluationPort> none = mock(ObjectProvider.class);
        when(none.getIfUnique(any())).thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(0)).get());

        RuleTransitionGuard unwired = new RuleTransitionGuard(none);

        assertThat(unwired.evaluate(contextWith(Map.of(RuleTransitionGuard.CONTEXT_PARAM, "order-approval"))).isAllowed()).isTrue();
    }

    private static TransitionContext contextWith(Map<String, Object> guardParams) {
        return new TransitionContext(ORG, OBJECT_ID, "order", null, null,
                new EntityObjectSnapshot(OBJECT_ID, 1L, PAYLOAD), guardParams, Map.of());
    }
}
