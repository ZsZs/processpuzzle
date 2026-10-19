package com.processpuzzle.calculation.i18n;

import com.processpuzzle.core.i18n.TranslationBundleKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Reads and writes this feature's translation bundles, keyed by organization, scope and locale. */
public interface CalculationTranslationRepository extends JpaRepository<CalculationTranslationBundle, TranslationBundleKey> {

    /** Every bundle of a tenant, in one statement, for erasing the tenant. */
    @Modifying(flushAutomatically = true)
    @Query("delete from CalculationTranslationBundle b where b.key.orgKey = :orgKey")
    int eraseByOrgKey(@Param("orgKey") String orgKey);
}
