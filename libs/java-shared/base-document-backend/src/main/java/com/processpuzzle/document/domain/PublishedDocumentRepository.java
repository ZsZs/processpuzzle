package com.processpuzzle.document.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * The only route to published content, and therefore the whole of what the public read path can
 * reach. Deliberately a separate repository from {@link DocumentDraftRepository} rather than a
 * flag on shared queries: a public endpoint that holds this interface has no method that could
 * return a draft, so the guarantee is enforced by the type it depends on.
 *
 * <p>Every finder is {@code orgKey}-scoped, as everywhere else in this module.
 */
public interface PublishedDocumentRepository extends JpaRepository<PublishedDocument, DocumentTranslationKey> {

    Optional<PublishedDocument> findByOrgKeyAndDocumentIdAndLocale(String orgKey, String documentId, String locale);

    List<PublishedDocument> findByOrgKeyAndDocumentId(String orgKey, String documentId);

    boolean existsByOrgKeyAndDocumentId(String orgKey, String documentId);

    void deleteByOrgKeyAndDocumentIdAndLocale(String orgKey, String documentId, String locale);

    void deleteByOrgKeyAndDocumentId(String orgKey, String documentId);

    void deleteByOrgKey(String orgKey);
}
