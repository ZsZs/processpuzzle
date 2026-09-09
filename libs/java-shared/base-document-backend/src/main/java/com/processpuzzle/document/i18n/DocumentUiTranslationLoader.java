package com.processpuzzle.document.i18n;

import com.processpuzzle.core.i18n.TranslationBundleImporter;
import com.processpuzzle.core.i18n.TranslationBundleLoaderSupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds this feature's bundled translations on startup, so a fresh deployment serves the scopes the
 * library owns instead of nothing. Gated behind {@code base-document.loadDefaultTranslations=true}.
 *
 * <p>The scan, the {@code <orgKey>-translations.yaml} naming convention and the per-file tally belong to
 * {@link TranslationBundleImporter} in processpuzzle-core; what stays here is where a bundle lands. That
 * division is the point — the seven features share how a seed file is read and share nothing about where
 * it is stored, which is what keeps them separable into services with separate databases.
 *
 * <p><strong>Unlike the other default loaders, an existing bundle is merged into rather than left
 * untouched.</strong> Several jars may contribute keys to one {@code (orgKey, scope, locale)}, so
 * skipping would let classpath enumeration order decide whose keys survive. The trade is that a seeded key
 * does overwrite an edited one on restart — bundles are shipped defaults, not tenant content.
 *
 * <p>Nothing here can fail startup: every problem is logged and the next bundle is attempted.
 */
@Component
@ConditionalOnProperty(prefix = "base-document", name = "loadDefaultTranslations", havingValue = "true")
public class DocumentUiTranslationLoader {

    private final TranslationBundleLoaderSupport<DocumentUiTranslationBundle> loaderSupport;

    public DocumentUiTranslationLoader(DocumentUiTranslationRepository repository, TranslationBundleImporter importer) {
        loaderSupport = new TranslationBundleLoaderSupport<>(
                importer, repository::findById, repository::save, DocumentUiTranslationBundle::new);
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void loadDefaults() {
        loaderSupport.loadDefaults("base-document");
    }
}
