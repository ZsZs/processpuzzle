package com.processpuzzle.rule.usecase;

import com.processpuzzle.rule.adapter.inbound.RuleMapper;
import com.processpuzzle.rule.domain.RuleDefinition;
import com.processpuzzle.rule.domain.RuleDefinitionRepository;
import com.processpuzzle.rule.domain.Severity;
import com.processpuzzle.rule.model.RuleDefinitionInput;
import com.processpuzzle.rule.usecase.exception.RuleAlreadyExistsException;
import com.processpuzzle.rule.usecase.service.RuleEngineSync;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CreateRuleTest {

    private RuleDefinitionRepository repository;
    private RuleEngineSync ruleEngineSync;
    private RuleExtendsValidator extendsValidator;
    private CreateRule createRule;

    @BeforeEach
    void setUp() {
        repository = mock(RuleDefinitionRepository.class);
        ruleEngineSync = mock(RuleEngineSync.class);
        extendsValidator = mock(RuleExtendsValidator.class);
        createRule = new CreateRule(repository, ruleEngineSync, extendsValidator, new RuleMapper());
    }

    @Test
    void persistsTheMappedRuleAndRegistersItWithTheEngine() {
        when(repository.existsByOrgKeyAndId("demo", "max-quantity")).thenReturn(false);
        when(repository.save(any(RuleDefinition.class))).thenAnswer(call -> call.getArgument(0));

        RuleDefinition saved = createRule.execute("demo", input("max-quantity", "parent"));

        assertThat(saved.getOrgKey()).isEqualTo("demo");
        assertThat(saved.getId()).isEqualTo("max-quantity");
        assertThat(saved.getContext()).isEqualTo("Order");
        assertThat(saved.getSeverity()).isEqualTo(Severity.WARNING);
        assertThat(saved.getFields()).containsExactly("quantity");
        verify(ruleEngineSync).register(saved);
    }

    @Test
    void validatesTheExtendsLinkBeforePersisting() {
        when(repository.existsByOrgKeyAndId("demo", "child")).thenReturn(false);
        when(repository.save(any(RuleDefinition.class))).thenAnswer(call -> call.getArgument(0));

        createRule.execute("demo", input("child", "base"));

        InOrder inOrder = inOrder(extendsValidator, repository, ruleEngineSync);
        inOrder.verify(extendsValidator).validate("demo", "child", "base");
        inOrder.verify(repository).save(any(RuleDefinition.class));
        inOrder.verify(ruleEngineSync).register(any(RuleDefinition.class));
    }

    @Test
    void refusesAnIdThatTheOrganizationAlreadyUses() {
        // An assigned composite id makes save() a merge, so without this check a create would
        // silently overwrite the existing rule instead of conflicting.
        when(repository.existsByOrgKeyAndId("demo", "max-quantity")).thenReturn(true);

        assertThatThrownBy(() -> createRule.execute("demo", input("max-quantity", null)))
                .isInstanceOf(RuleAlreadyExistsException.class)
                .hasMessage("Rule already exists: demo/max-quantity");

        verify(repository, never()).save(any(RuleDefinition.class));
        verifyNoInteractions(extendsValidator, ruleEngineSync);
    }

    private static RuleDefinitionInput input(String id, String extendsRuleId) {
        return new RuleDefinitionInput(id, "Max quantity", "Order", "entity.quantity <= 5",
                com.processpuzzle.rule.model.Severity.WARNING)
                .extendsRuleId(extendsRuleId)
                .fields(List.of("quantity"));
    }
}
