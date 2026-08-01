package com.processpuzzle.rule.usecase;

import com.processpuzzle.rule.adapter.inbound.RuleMapper;
import com.processpuzzle.rule.domain.RuleDefinition;
import com.processpuzzle.rule.domain.RuleDefinitionRepository;
import com.processpuzzle.rule.domain.Severity;
import com.processpuzzle.rule.model.RuleDefinitionInput;
import com.processpuzzle.rule.usecase.exception.RuleNotFoundException;
import com.processpuzzle.rule.usecase.service.RuleEngineSync;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UpdateRuleTest {

    private RuleDefinitionRepository repository;
    private RuleEngineSync ruleEngineSync;
    private RuleExtendsValidator extendsValidator;
    private UpdateRule updateRule;

    @BeforeEach
    void setUp() {
        repository = mock(RuleDefinitionRepository.class);
        ruleEngineSync = mock(RuleEngineSync.class);
        extendsValidator = mock(RuleExtendsValidator.class);
        updateRule = new UpdateRule(repository, ruleEngineSync, extendsValidator, new RuleMapper());
    }

    @Test
    void appliesTheInputToTheExistingRowRatherThanReplacingIt() {
        RuleDefinition existing = existing();
        when(repository.findByOrgKeyAndId("demo", "max-quantity")).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        RuleDefinition saved = updateRule.execute("demo", "max-quantity", input(null));

        assertThat(saved).isSameAs(existing);
        assertThat(saved.getOrgKey()).isEqualTo("demo");
        assertThat(saved.getName()).isEqualTo("Renamed");
        assertThat(saved.getExpression()).isEqualTo("entity.quantity <= 9");
        assertThat(saved.getSeverity()).isEqualTo(Severity.INFO);
        assertThat(saved.getFields()).containsExactly("quantity");
        verify(extendsValidator).validate("demo", "max-quantity", null);
        verify(ruleEngineSync).register(existing);
    }

    @Test
    void validatesTheNewExtendsLinkAgainstTheIdFromThePath() {
        RuleDefinition existing = existing();
        when(repository.findByOrgKeyAndId("demo", "max-quantity")).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        updateRule.execute("demo", "max-quantity", input("base"));

        verify(extendsValidator).validate("demo", "max-quantity", "base");
        assertThat(existing.getExtendsRuleId()).isEqualTo("base");
    }

    @Test
    void failsWhenTheOrganizationHasNoSuchRule() {
        when(repository.findByOrgKeyAndId("demo", "missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> updateRule.execute("demo", "missing", input(null)))
                .isInstanceOf(RuleNotFoundException.class)
                .hasMessage("Rule not found: demo/missing");

        verify(repository, never()).save(any(RuleDefinition.class));
        verifyNoInteractions(extendsValidator, ruleEngineSync);
    }

    private static RuleDefinition existing() {
        return new RuleDefinition("demo", "max-quantity", "Max quantity", "desc", "Order",
                "entity.quantity <= 5", Severity.ERROR, "violated", "rule.max", null,
                false, true, List.of("total"));
    }

    private static RuleDefinitionInput input(String extendsRuleId) {
        return new RuleDefinitionInput("ignored-body-id", "Renamed", "Order", "entity.quantity <= 9",
                com.processpuzzle.rule.model.Severity.INFO)
                .extendsRuleId(extendsRuleId)
                .fields(List.of("quantity"));
    }
}
