package com.processpuzzle.app.i18n;

import com.processpuzzle.core.i18n.TranslationBundleKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Reads and writes this feature's translation bundles, keyed by organization, scope and locale. */
public interface AppTranslationRepository extends JpaRepository<AppTranslationBundle, TranslationBundleKey> {

    /** Every bundle of a tenant, in one statement, for erasing the tenant. */
    @Modifying(flushAutomatically = true)
    @Query("delete from AppTranslationBundle b where b.key.orgKey = :orgKey")
    int eraseByOrgKey(@Param("orgKey") String orgKey);
}
