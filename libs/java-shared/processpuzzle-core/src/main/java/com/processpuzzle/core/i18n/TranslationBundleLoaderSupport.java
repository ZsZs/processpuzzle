package com.processpuzzle.core.i18n;

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/** Shared persistence behavior for feature-owned default translation loaders. */
public final class TranslationBundleLoaderSupport<T extends AbstractTranslationBundle> {

    private final TranslationBundleImporter importer;
    private final Function<TranslationBundleKey, Optional<T>> findById;
    private final Consumer<T> save;
    private final TranslationBundleFactory<T> factory;

    public TranslationBundleLoaderSupport(
            TranslationBundleImporter importer,
            Function<TranslationBundleKey, Optional<T>> findById,
            Consumer<T> save,
            TranslationBundleFactory<T> factory) {
        this.importer = importer;
        this.findById = findById;
        this.save = save;
        this.factory = factory;
    }

    public void loadDefaults(String featureName) {
        importer.importAll(featureName, this::store);
    }

    private TranslationBundleImporter.Outcome store(String orgKey, TranslationBundleDocument.Entry entry) {
        TranslationBundleKey key = new TranslationBundleKey(orgKey, entry.scope(), entry.locale());
        return findById.apply(key)
                .map(existing -> {
                    existing.setMessages(TranslationBundleImporter.deepMerge(existing.getMessages(), entry.messages()));
                    save.accept(existing);
                    return TranslationBundleImporter.Outcome.MERGED;
                })
                .orElseGet(() -> {
                    save.accept(factory.create(orgKey, entry.scope(), entry.locale(), entry.messages()));
                    return TranslationBundleImporter.Outcome.CREATED;
                });
    }

    @FunctionalInterface
    public interface TranslationBundleFactory<T extends AbstractTranslationBundle> {
        T create(String orgKey, String scope, String locale, Map<String, Object> messages);
    }
}
