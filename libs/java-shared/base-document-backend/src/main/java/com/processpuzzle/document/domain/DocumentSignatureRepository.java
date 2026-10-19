package com.processpuzzle.document.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Every finder is {@code orgKey}-scoped, as everywhere else in this module. */
public interface DocumentSignatureRepository extends JpaRepository<DocumentSignature, String> {

    List<DocumentSignature> findByOrgKeyAndDocumentIdOrderBySignedAtDesc(String orgKey, String documentId);

    Optional<DocumentSignature> findByOrgKeyAndIdAndDocumentId(String orgKey, String id, String documentId);

    /** The issued documents: every signature of the tenant, newest first, each filter ignored when null. */
    @Query("""
            select s from DocumentSignature s
            where s.orgKey = :orgKey
              and (:documentId is null or s.documentId = :documentId)
              and (:subjectType is null or s.subjectType = :subjectType)
              and (:subjectId is null or s.subjectId = :subjectId)
            order by s.signedAt desc, s.id""")
    Page<DocumentSignature> findIssued(@Param("orgKey") String orgKey, @Param("documentId") String documentId,
                                       @Param("subjectType") String subjectType, @Param("subjectId") String subjectId,
                                       Pageable pageable);

    @SuppressWarnings("java:S107") // a derived query names every column it matches
    Optional<DocumentSignature> findFirstByOrgKeyAndDocumentIdAndLocaleAndRevisionAndSubjectTypeAndSubjectIdAndSignedBy(
            String orgKey, String documentId, String locale, Long revision, String subjectType, String subjectId,
            String signedBy);

    /** Every signature of a tenant in one statement, for erasing the tenant. */
    @Modifying(flushAutomatically = true)
    @Query("delete from DocumentSignature s where s.orgKey = :orgKey")
    int eraseByOrgKey(@Param("orgKey") String orgKey);
}
