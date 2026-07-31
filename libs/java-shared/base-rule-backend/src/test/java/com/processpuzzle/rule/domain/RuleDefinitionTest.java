package com.processpuzzle.rule.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleDefinitionTest {

    @Test
    void theFieldlessConstructorLeavesAnEmptyFieldList() {
        RuleDefinition rule = new RuleDefinition("demo", "max-quantity", "Max quantity", "desc",
                "Order", "true", Severity.WARNING, "violated", "rule.max", "base", true, false);

        assertThat(rule.getOrgKey()).isEqualTo("demo");
        assertThat(rule.getId()).isEqualTo("max-quantity");
        assertThat(rule.getSeverity()).isEqualTo(Severity.WARNING);
        assertThat(rule.getExtendsRuleId()).isEqualTo("base");
        assertThat(rule.isOverride()).isTrue();
        assertThat(rule.isEnabled()).isFalse();
        assertThat(rule.getFields()).isEmpty();
    }

    @Test
    void aNullFieldListIsNormalisedToAnEmptyOne() {
        RuleDefinition rule = rule(List.of("quantity"));

        rule.setFields(null);

        assertThat(rule.getFields()).isEmpty();
    }

    @Test
    void theFieldListIsCopiedInAndHandedOutReadOnly() {
        List<String> mutable = new ArrayList<>(List.of("quantity"));
        RuleDefinition rule = rule(mutable);

        mutable.add("smuggled");

        assertThat(rule.getFields()).containsExactly("quantity");
        assertThatThrownBy(() -> rule.getFields().add("smuggled"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static RuleDefinition rule(List<String> fields) {
        return new RuleDefinition("demo", "max-quantity", "Max quantity", null, "Order", "true",
                Severity.ERROR, null, null, null, false, true, fields);
    }
}
