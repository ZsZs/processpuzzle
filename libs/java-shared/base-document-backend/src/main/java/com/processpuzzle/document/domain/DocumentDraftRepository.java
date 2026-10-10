package com.processpuzzle.document.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Every finder is scoped by {@code orgKey}, exactly as {@link DocumentRepository} documents: the
 * inherited {@code findById}/{@code existsById} take a {@link DocumentTranslationKey}, so an
 * unscoped read of another tenant's content is not expressible by accident.
 */
public interface DocumentDraftRepository extends JpaRepository<DocumentDraft, DocumentTranslationKey> {

    Optional<DocumentDraft> findByOrgKeyAndDocumentIdAndLocale(String orgKey, String documentId, String locale);

    boolean existsByOrgKeyAndDocumentIdAndLocale(String orgKey, String documentId, String locale);

    List<DocumentDraft> findByOrgKeyAndDocumentId(String orgKey, String documentId);

    void deleteByOrgKeyAndDocumentId(String orgKey, String documentId);

    void deleteByOrgKey(String orgKey);

    /**
     * Every draft of a tenant in one statement, for erasing the tenant. Not {@link #deleteByOrgKey},
     * which loads every row and removes it one by one.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from DocumentDraft d where d.orgKey = :orgKey")
    int eraseByOrgKey(@Param("orgKey") String orgKey);
}
