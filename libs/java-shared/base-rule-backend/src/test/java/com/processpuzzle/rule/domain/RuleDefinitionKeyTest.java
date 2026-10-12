package com.processpuzzle.rule.domain;

import com.processpuzzle.core.definition.Channel;
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
        key.setChannel(Channel.DRAFT);

        assertThat(key.getOrgKey()).isEqualTo("demo");
        assertThat(key.getId()).isEqualTo("max-quantity");
        assertThat(key).isEqualTo(new RuleDefinitionKey("demo", "max-quantity", Channel.DRAFT));
    }

    @Test
    void equalityCoversTheOrganizationAndTheRuleId() {
        RuleDefinitionKey key = new RuleDefinitionKey("demo", "max-quantity", Channel.DRAFT);

        assertThat(key).isEqualTo(key);
        assertThat(key).isEqualTo(new RuleDefinitionKey("demo", "max-quantity", Channel.DRAFT));
        assertThat(key).hasSameHashCodeAs(new RuleDefinitionKey("demo", "max-quantity", Channel.DRAFT));
        // Same rule id, different tenant — deliberately not equal.
        assertThat(key).isNotEqualTo(new RuleDefinitionKey("other", "max-quantity", Channel.DRAFT));
        assertThat(key).isNotEqualTo(new RuleDefinitionKey("demo", "other-rule", Channel.DRAFT));
        assertThat(key).isNotEqualTo(new RuleDefinitionKey());
    }

    @Test
    void isNeitherEqualToNullNorToAnUnrelatedType() {
        RuleDefinitionKey key = new RuleDefinitionKey("demo", "max-quantity", Channel.DRAFT);

        assertThat(key.equals(null)).isFalse();
        assertThat(key.equals("demo/max-quantity")).isFalse();
    }

    @Test
    void rendersAsOrganizationSlashId() {
        assertThat(new RuleDefinitionKey("demo", "max-quantity", Channel.DRAFT)).hasToString("demo/max-quantity");
    }
}
