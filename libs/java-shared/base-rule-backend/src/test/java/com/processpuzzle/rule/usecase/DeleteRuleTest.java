package com.processpuzzle.rule.usecase;

import com.processpuzzle.rule.domain.RuleDefinition;
import com.processpuzzle.rule.domain.RuleDefinitionRepository;
import com.processpuzzle.rule.domain.Severity;
import com.processpuzzle.rule.usecase.exception.RuleNotFoundException;
import com.processpuzzle.rule.usecase.service.RuleEngineSync;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DeleteRuleTest {

    private RuleDefinitionRepository repository;
    private RuleEngineSync ruleEngineSync;
    private DeleteRule deleteRule;

    @BeforeEach
    void setUp() {
        repository = mock(RuleDefinitionRepository.class);
        ruleEngineSync = mock(RuleEngineSync.class);
        deleteRule = new DeleteRule(repository, ruleEngineSync);
    }

    @Test
    void deletesTheRowAndUnregistersTheCompiledRule() {
        RuleDefinition existing = rule("base", null);
        when(repository.findByOrgKeyAndId("demo", "base")).thenReturn(Optional.of(existing));
        when(repository.findByOrgKeyAndExtendsRuleId("demo", "base")).thenReturn(List.of());

        deleteRule.execute("demo", "base");

        verify(repository).delete(existing);
        verify(ruleEngineSync).unregister("demo", "base");
    }

    @Test
    void failsWhenTheOrganizationHasNoSuchRule() {
        when(repository.findByOrgKeyAndId("demo", "missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deleteRule.execute("demo", "missing"))
                .isInstanceOf(RuleNotFoundException.class)
                .hasMessage("Rule not found: demo/missing");

        verify(repository, never()).delete(any(RuleDefinition.class));
        verifyNoInteractions(ruleEngineSync);
    }

    @Test
    void refusesWhileDependentsStillExtendTheRule() {
        when(repository.findByOrgKeyAndId("demo", "base")).thenReturn(Optional.of(rule("base", null)));
        when(repository.findByOrgKeyAndExtendsRuleId("demo", "base"))
                .thenReturn(List.of(rule("child-a", "base"), rule("child-b", "base")));

        assertThatThrownBy(() -> deleteRule.execute("demo", "base"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot delete rule 'base': still extended by [child-a, child-b]");

        verify(repository, never()).delete(any(RuleDefinition.class));
        verifyNoInteractions(ruleEngineSync);
    }

    private static RuleDefinition rule(String id, String extendsRuleId) {
        return new RuleDefinition("demo", id, id, null, "Order", "true", Severity.ERROR,
                null, null, extendsRuleId, false, true, List.of());
    }
}
