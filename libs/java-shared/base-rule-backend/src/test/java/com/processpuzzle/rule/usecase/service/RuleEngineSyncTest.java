package com.processpuzzle.rule.usecase.service;

import com.processpuzzle.rule.domain.RuleDefinition;
import com.processpuzzle.rule.domain.Severity;
import com.processpuzzle.rule.usecase.engine.RuleEngine;
import com.processpuzzle.rule.usecase.engine.RuleKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RuleEngineSyncTest {

    private RuleEngine ruleEngine;
    private RuleEngineSync ruleEngineSync;

    @BeforeEach
    void setUp() {
        ruleEngine = mock(RuleEngine.class);
        ruleEngineSync = new RuleEngineSync(ruleEngine);
    }

    @Test
    void registersAnEnabledRuleUnderItsTenantScopedKey() {
        ruleEngineSync.register(rule(true));

        verify(ruleEngine).registerRule(RuleKey.of("demo", "max-quantity"), "entity.quantity <= 5");
        verify(ruleEngine, never()).unregisterRule(any(RuleKey.class));
    }

    @Test
    void unregistersInsteadOfRegisteringWhenTheRuleIsDisabled() {
        // Disabling is the same thing as removal as far as the engine is concerned.
        ruleEngineSync.register(rule(false));

        verify(ruleEngine).unregisterRule(RuleKey.of("demo", "max-quantity"));
        verify(ruleEngine, never()).registerRule(any(RuleKey.class), anyString());
    }

    @Test
    void unregisterTargetsOnlyTheGivenOrganizationsRule() {
        ruleEngineSync.unregister("demo", "max-quantity");

        verify(ruleEngine).unregisterRule(RuleKey.of("demo", "max-quantity"));
        verify(ruleEngine, never()).unregisterRule(RuleKey.of("other", "max-quantity"));
    }

    private static RuleDefinition rule(boolean enabled) {
        return new RuleDefinition("demo", "max-quantity", "Max quantity", null, "Order",
                "entity.quantity <= 5", Severity.ERROR, null, null, null, false, enabled, List.of());
    }
}
