package com.processpuzzle.app.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The composite key is what makes {@code claims-app} mean a different application in each tenant, so
 * it has to compare on both halves. It is also a JPA {@code @IdClass}, which is why it is a mutable
 * class with a no-arg constructor rather than a record — Hibernate populates it field by field.
 */
class AppDefinitionKeyTest {

    @Test
    void carriesBothHalvesOfTheIdentity() {
        AppDefinitionKey key = new AppDefinitionKey("my-org", "claims-app");

        assertThat(key.getOrgKey()).isEqualTo("my-org");
        assertThat(key.getId()).isEqualTo("claims-app");
    }

    /** Hibernate instantiates the key empty and fills it in through the setters. */
    @Test
    void isPopulatableFieldByFieldTheWayHibernateDoesIt() {
        AppDefinitionKey key = new AppDefinitionKey();

        assertThat(key.getOrgKey()).isNull();
        assertThat(key.getId()).isNull();

        key.setOrgKey("my-org");
        key.setId("claims-app");

        assertThat(key).isEqualTo(new AppDefinitionKey("my-org", "claims-app"));
    }

    @Test
    void twoTenantsMayBothOwnTheSameAppId() {
        AppDefinitionKey mine = new AppDefinitionKey("my-org", "claims-app");
        AppDefinitionKey theirs = new AppDefinitionKey("other-org", "claims-app");

        assertThat(mine).isNotEqualTo(theirs);
        assertThat(mine).isNotEqualTo(new AppDefinitionKey("my-org", "policy-app"));
    }

    @Test
    void equalKeysShareAHashCodeSoTheyCollapseInAMap() {
        AppDefinitionKey one = new AppDefinitionKey("my-org", "claims-app");
        AppDefinitionKey other = new AppDefinitionKey("my-org", "claims-app");

        assertThat(one).isEqualTo(other).hasSameHashCodeAs(other);
        assertThat(new java.util.HashSet<>(java.util.List.of(one, other))).hasSize(1);
    }

    @Test
    void comparesEqualToItselfAndToNothingOfAnotherType() {
        AppDefinitionKey key = new AppDefinitionKey("my-org", "claims-app");

        assertThat(key.equals(key)).isTrue();
        assertThat(key.equals("my-org/claims-app")).isFalse();
        assertThat(key.equals(null)).isFalse();
    }

    /** Read in log lines and constraint-violation messages, so the shape is worth pinning down. */
    @Test
    void readsAsTenantSlashApp() {
        assertThat(new AppDefinitionKey("my-org", "claims-app")).hasToString("my-org/claims-app");
    }
}
