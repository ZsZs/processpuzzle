package com.processpuzzle.core.i18n;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AbstractTranslationBundleTest {

    @Test
    void initializesAnEmptyBundleForJpa() {
        TestTranslationBundle bundle = new TestTranslationBundle();

        assertThat(bundle.getKey().getOrgKey()).isNull();
        assertThat(bundle.getKey().getScope()).isNull();
        assertThat(bundle.getKey().getLocale()).isNull();
        assertThat(bundle.getMessages()).isEmpty();

        bundle.getKey().setOrgKey("acme");
        bundle.getKey().setScope("orders");
        bundle.getKey().setLocale("en");
        bundle.setMessages(null);

        assertThat(bundle.getKey().getOrgKey()).isEqualTo("acme");
        assertThat(bundle.getKey().getScope()).isEqualTo("orders");
        assertThat(bundle.getKey().getLocale()).isEqualTo("en");
        assertThat(bundle.getMessages()).isEmpty();
    }

    @Test
    void copiesConstructorMessagesButAcceptsReplacementMessages() {
        Map<String, Object> constructorMessages = new LinkedHashMap<>(Map.of("title", "Orders"));
        TestTranslationBundle bundle = new TestTranslationBundle("acme", "orders", "en", constructorMessages);

        constructorMessages.put("hint", "Manage orders");

        assertThat(bundle.getMessages()).containsOnly(Map.entry("title", "Orders"));

        Map<String, Object> replacementMessages = new LinkedHashMap<>(Map.of("title", "Bestellungen"));
        bundle.setMessages(replacementMessages);
        replacementMessages.put("hint", "Bestellungen verwalten");

        assertThat(bundle.getMessages()).containsOnly(
                Map.entry("title", "Bestellungen"),
                Map.entry("hint", "Bestellungen verwalten"));
    }

    @Test
    void initializesAnEmptyMessageMapWhenConstructorMessagesAreMissing() {
        TestTranslationBundle bundle = new TestTranslationBundle("acme", "orders", "en", null);

        assertThat(bundle.getMessages()).isEmpty();
    }

    private static final class TestTranslationBundle extends AbstractTranslationBundle {

        private TestTranslationBundle() {
            super();
        }

        private TestTranslationBundle(String orgKey, String scope, String locale, Map<String, Object> messages) {
            super(orgKey, scope, locale, messages);
        }
    }
}
