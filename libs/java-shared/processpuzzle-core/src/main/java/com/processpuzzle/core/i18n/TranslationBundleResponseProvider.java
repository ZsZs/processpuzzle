package com.processpuzzle.core.i18n;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.http.ResponseEntity;

/** Reads a translation bundle and represents a missing bundle as an empty response object. */
public final class TranslationBundleResponseProvider<T extends AbstractTranslationBundle> {

    private final Function<TranslationBundleKey, Optional<T>> findById;

    public TranslationBundleResponseProvider(Function<TranslationBundleKey, Optional<T>> findById) {
        this.findById = findById;
    }

    public ResponseEntity<Map<String, Object>> bundle(String orgKey, String scope, String locale) {
        return ResponseEntity.ok(findById.apply(new TranslationBundleKey(orgKey, scope, locale))
                .map(AbstractTranslationBundle::getMessages)
                .orElseGet(Map::of));
    }
}
