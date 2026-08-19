package com.processpuzzle.baseentity.i18n;

import com.processpuzzle.core.i18n.TranslationBundleKey;
import org.springframework.data.jpa.repository.JpaRepository;

/** Reads and writes this feature's translation bundles, keyed by organization, scope and locale. */
public interface EntityTranslationRepository extends JpaRepository<EntityTranslationBundle, TranslationBundleKey> {
}
