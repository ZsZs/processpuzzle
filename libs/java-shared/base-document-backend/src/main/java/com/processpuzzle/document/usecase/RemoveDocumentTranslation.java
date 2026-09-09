package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentDraftRepository;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.domain.PublishedDocumentRepository;
import com.processpuzzle.document.domain.DocumentTranslationKey;
import com.processpuzzle.document.usecase.exception.DocumentNotFoundException;
import com.processpuzzle.document.usecase.exception.DocumentPublishingConflictException;
import com.processpuzzle.document.usecase.exception.DocumentTranslationNotFoundException;
import com.processpuzzle.document.usecase.service.DocumentGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Removes a locale entirely — draft and published snapshot alike.
 *
 * <p>Refused for the source locale. Every other translation's {@code basedOnRevision} is measured
 * against it, and readers whose language is missing fall back to it, so removing it would leave
 * both without an answer. Changing {@code sourceLocale} through the properties endpoint first is
 * the deliberate two-step.
 */
@Service
@Transactional
public class RemoveDocumentTranslation {

    private final DocumentRepository repository;
    private final DocumentDraftRepository draftRepository;
    private final PublishedDocumentRepository publishedRepository;
    private final DocumentGuard guard;

    public RemoveDocumentTranslation(DocumentRepository repository,
                                     DocumentDraftRepository draftRepository,
                                     PublishedDocumentRepository publishedRepository,
                                     DocumentGuard guard) {
        this.repository = repository;
        this.draftRepository = draftRepository;
        this.publishedRepository = publishedRepository;
        this.guard = guard;
    }

    public void execute(String orgKey, String documentId, String locale) {
        Document document = repository.findByOrgKeyAndId(orgKey, documentId)
                .orElseThrow(() -> new DocumentNotFoundException(orgKey, documentId));
        guard.requireEditor(document);

        if (document.getSourceLocale().equals(locale)) {
            throw DocumentPublishingConflictException.sourceLocaleNotRemovable(documentId, locale);
        }
        if (!draftRepository.existsByOrgKeyAndDocumentIdAndLocale(orgKey, documentId, locale)) {
            throw new DocumentTranslationNotFoundException(orgKey, documentId, locale);
        }

        publishedRepository.deleteByOrgKeyAndDocumentIdAndLocale(orgKey, documentId, locale);
        draftRepository.deleteById(new DocumentTranslationKey(orgKey, documentId, locale));
    }
}
