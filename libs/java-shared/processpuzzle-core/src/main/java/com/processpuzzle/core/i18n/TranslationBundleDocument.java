package com.processpuzzle.core.i18n;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.List;
import java.util.Map;

/**
 * Root of a bundled {@code default-translations/<orgKey>-translations.yaml}, read by
 * {@link TranslationBundleImporter} on startup.
 *
 * <p>Every backend feature library may ship one, and they all carry the same file name for a given
 * tenant — the importer scans with {@code classpath*:}, so one file per jar is found and all of them are
 * loaded. That is the point: a library seeds the scopes it owns without knowing what the others seed.
 *
 * @param translations the bundles to seed
 */
public record TranslationBundleDocument(
        @JsonAlias({"translations", "bundles"})
        List<Entry> translations
) {

    public TranslationBundleDocument {
        translations = translations == null ? List.of() : List.copyOf(translations);
    }

    /**
     * One bundle.
     *
     * <p>{@code messages} holds keys <em>relative to the scope</em>, exactly as the asset files do:
     * transloco prefixes them with the scope alias itself, so wrapping them in another {@code scope:}
     * level would resolve as {@code <scope>.<scope>.key} and match nothing.
     *
     * @param scope    transloco scope; absent means the application's root bundle, stored under
     *                 {@link AbstractTranslationBundle#ROOT_SCOPE}
     * @param locale   BCP-47 language tag, matching the {@code <lang>.json} the asset would have been
     * @param messages the message map
     */
    public record Entry(String scope, String locale, Map<String, Object> messages) {

        public Entry {
            scope = scope == null || scope.isBlank() ? AbstractTranslationBundle.ROOT_SCOPE : scope.trim();
            messages = messages == null ? Map.of() : Map.copyOf(messages);
        }
    }
}
