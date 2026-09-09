package com.processpuzzle.core.i18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TranslationBundleDocumentTest {

    @Test
    void normalizesMissingDocumentAndEntryValues() {
        TranslationBundleDocument document = new TranslationBundleDocument(null);
        TranslationBundleDocument.Entry entry = new TranslationBundleDocument.Entry(null, "en", null);

        assertThat(document.translations()).isEmpty();
        assertThat(entry.scope()).isEqualTo(AbstractTranslationBundle.ROOT_SCOPE);
        assertThat(entry.locale()).isEqualTo("en");
        assertThat(entry.messages()).isEmpty();
    }

    @Test
    void copiesDocumentAndEntryCollectionsAndNormalizesScope() {
        Map<String, Object> messages = new java.util.LinkedHashMap<>(Map.of("title", "Orders"));
        TranslationBundleDocument.Entry entry = new TranslationBundleDocument.Entry(" orders ", "en", messages);
        List<TranslationBundleDocument.Entry> translations = new ArrayList<>(List.of(entry));

        TranslationBundleDocument document = new TranslationBundleDocument(translations);
        translations.clear();
        messages.put("hint", "Manage orders");

        assertThat(document.translations()).containsExactly(entry);
        assertThat(entry.scope()).isEqualTo("orders");
        assertThat(entry.messages()).containsOnly(Map.entry("title", "Orders"));
        List<TranslationBundleDocument.Entry> documentTranslations = document.translations();

        assertThatThrownBy(() -> documentTranslations.add(entry)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void treatsBlankScopeAsTheRootBundle() {
        TranslationBundleDocument.Entry entry = new TranslationBundleDocument.Entry("   ", "de", Map.of());

        assertThat(entry.scope()).isEqualTo(AbstractTranslationBundle.ROOT_SCOPE);
    }
}
