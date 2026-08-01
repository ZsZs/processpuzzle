package com.processpuzzle.rule.usecase;

import com.processpuzzle.rule.domain.RuleDefinition;
import com.processpuzzle.rule.domain.RuleDefinitionRepository;
import com.processpuzzle.rule.domain.Severity;
import com.processpuzzle.rule.usecase.exception.RuleNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FindRuleTest {

    private RuleDefinitionRepository repository;
    private FindRule findRule;

    @BeforeEach
    void setUp() {
        repository = mock(RuleDefinitionRepository.class);
        findRule = new FindRule(repository);
    }

    @Test
    void readsTheRuleThroughTheOrgScopedFinder() {
        RuleDefinition rule = new RuleDefinition("demo", "base", "Base", null, "Order", "true",
                Severity.ERROR, null, null, null, false, true, List.of());
        when(repository.findByOrgKeyAndId("demo", "base")).thenReturn(Optional.of(rule));

        assertThat(findRule.execute("demo", "base")).isSameAs(rule);
    }

    @Test
    void failsWhenTheRuleBelongsToAnotherOrganizationOrDoesNotExist() {
        when(repository.findByOrgKeyAndId("demo", "base")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> findRule.execute("demo", "base"))
                .isInstanceOf(RuleNotFoundException.class)
                .hasMessage("Rule not found: demo/base");
    }
}
