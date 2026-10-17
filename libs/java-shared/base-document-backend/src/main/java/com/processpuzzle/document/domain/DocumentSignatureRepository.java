package com.processpuzzle.document.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Every finder is {@code orgKey}-scoped, as everywhere else in this module. */
public interface DocumentSignatureRepository extends JpaRepository<DocumentSignature, String> {

    List<DocumentSignature> findByOrgKeyAndDocumentIdOrderBySignedAtDesc(String orgKey, String documentId);

    @SuppressWarnings("java:S107") // a derived query names every column it matches
    Optional<DocumentSignature> findFirstByOrgKeyAndDocumentIdAndLocaleAndRevisionAndSubjectTypeAndSubjectIdAndSignedBy(
            String orgKey, String documentId, String locale, Long revision, String subjectType, String subjectId,
            String signedBy);

    /** Every signature of a tenant in one statement, for erasing the tenant. */
    @Modifying(flushAutomatically = true)
    @Query("delete from DocumentSignature s where s.orgKey = :orgKey")
    int eraseByOrgKey(@Param("orgKey") String orgKey);
}
