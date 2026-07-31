package com.processpuzzle.rule.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@code @IdClass} contract JPA relies on: a public no-arg constructor plus value equality
 * over <em>both</em> key columns. Equality on the rule id alone would make two tenants' rules
 * the same entity in the persistence context.
 */
class RuleDefinitionKeyTest {

    @Test
    void theNoArgConstructorJpaNeedsLeavesBothPartsUnset() {
        RuleDefinitionKey key = new RuleDefinitionKey();

        assertThat(key.getOrgKey()).isNull();
        assertThat(key.getId()).isNull();
    }

    @Test
    void bothPartsAreSettableAsJpaRequires() {
        RuleDefinitionKey key = new RuleDefinitionKey();

        key.setOrgKey("demo");
        key.setId("max-quantity");

        assertThat(key.getOrgKey()).isEqualTo("demo");
        assertThat(key.getId()).isEqualTo("max-quantity");
        assertThat(key).isEqualTo(new RuleDefinitionKey("demo", "max-quantity"));
    }

    @Test
    void equalityCoversTheOrganizationAndTheRuleId() {
        RuleDefinitionKey key = new RuleDefinitionKey("demo", "max-quantity");

        assertThat(key).isEqualTo(key);
        assertThat(key).isEqualTo(new RuleDefinitionKey("demo", "max-quantity"));
        assertThat(key).hasSameHashCodeAs(new RuleDefinitionKey("demo", "max-quantity"));
        // Same rule id, different tenant — deliberately not equal.
        assertThat(key).isNotEqualTo(new RuleDefinitionKey("other", "max-quantity"));
        assertThat(key).isNotEqualTo(new RuleDefinitionKey("demo", "other-rule"));
        assertThat(key).isNotEqualTo(new RuleDefinitionKey());
    }

    @Test
    void isNeitherEqualToNullNorToAnUnrelatedType() {
        RuleDefinitionKey key = new RuleDefinitionKey("demo", "max-quantity");

        assertThat(key.equals(null)).isFalse();
        assertThat(key.equals("demo/max-quantity")).isFalse();
    }

    @Test
    void rendersAsOrganizationSlashId() {
        assertThat(new RuleDefinitionKey("demo", "max-quantity")).hasToString("demo/max-quantity");
    }
}
