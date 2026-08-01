package com.processpuzzle.rule.usecase.engine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleKeyTest {

    @Test
    void carriesBothTheOrganizationAndTheRuleId() {
        RuleKey key = RuleKey.of("demo", "max-quantity");

        assertThat(key.orgKey()).isEqualTo("demo");
        assertThat(key.ruleId()).isEqualTo("max-quantity");
    }

    @Test
    void theSameRuleIdInTwoOrganizationsIsTwoDistinctKeys() {
        // The defect this type exists to prevent: a bare-id cache shared compiled expressions
        // across tenants.
        assertThat(RuleKey.of("demo", "max-quantity")).isNotEqualTo(RuleKey.of("other", "max-quantity"));
        assertThat(RuleKey.of("demo", "max-quantity")).isEqualTo(RuleKey.of("demo", "max-quantity"));
        assertThat(RuleKey.of("demo", "max-quantity"))
                .hasSameHashCodeAs(RuleKey.of("demo", "max-quantity"));
    }

    @Test
    void refusesToBeConstructedWithoutEitherPart() {
        assertThatThrownBy(() -> RuleKey.of(null, "max-quantity"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("orgKey is required");
        assertThatThrownBy(() -> RuleKey.of("demo", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("ruleId is required");
    }

    @Test
    void theGraalSourceNameNamesTheTenantSoStackTracesDoToo() {
        assertThat(RuleKey.of("demo", "max-quantity").asSourceName()).isEqualTo("demo/max-quantity.js");
    }

    @Test
    void rendersAsOrganizationSlashId() {
        assertThat(RuleKey.of("demo", "max-quantity")).hasToString("demo/max-quantity");
    }
}
