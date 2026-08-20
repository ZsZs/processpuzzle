package com.processpuzzle.core.i18n;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TranslationBundleKeyTest {

    @Test
    void carriesAllPartsOfTheIdentity() {
        TranslationBundleKey key = new TranslationBundleKey("acme", "orders", "en");

        assertThat(key.getOrgKey()).isEqualTo("acme");
        assertThat(key.getScope()).isEqualTo("orders");
        assertThat(key.getLocale()).isEqualTo("en");
        assertThat(key).hasToString("acme/orders/en");
    }

    @Test
    void isPopulatableFieldByFieldTheWayJpaDoesIt() {
        TranslationBundleKey key = new TranslationBundleKey();

        assertThat(key.getOrgKey()).isNull();
        assertThat(key.getScope()).isNull();
        assertThat(key.getLocale()).isNull();

        key.setOrgKey("acme");
        key.setScope("orders");
        key.setLocale("en");

        assertThat(key).isEqualTo(new TranslationBundleKey("acme", "orders", "en"));
    }

    @Test
    void distinguishesEachPartOfTheIdentity() {
        TranslationBundleKey key = new TranslationBundleKey("acme", "orders", "en");

        assertThat(key)
                .isNotEqualTo(new TranslationBundleKey("other", "orders", "en"))
                .isNotEqualTo(new TranslationBundleKey("acme", "catalog", "en"))
                .isNotEqualTo(new TranslationBundleKey("acme", "orders", "de"));
    }

    @Test
    void comparesEqualKeysByValue() {
        TranslationBundleKey one = new TranslationBundleKey("acme", "orders", "en");
        TranslationBundleKey other = new TranslationBundleKey("acme", "orders", "en");

        assertThat(one).isEqualTo(other).hasSameHashCodeAs(other);
        assertThat(new HashSet<>(List.of(one, other))).hasSize(1);
    }

    @Test
    void comparesEqualToItselfAndToNoOtherType() {
        TranslationBundleKey key = new TranslationBundleKey("acme", "orders", "en");

        assertThat(key.equals(key)).isTrue();
        assertThat(key.equals(null)).isFalse();
        assertThat(key.equals("acme/orders/en")).isFalse();
    }
}
