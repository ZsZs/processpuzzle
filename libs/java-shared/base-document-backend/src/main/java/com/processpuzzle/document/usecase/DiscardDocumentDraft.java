package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentDraft;
import com.processpuzzle.document.domain.DocumentDraftRepository;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.domain.PublishedDocument;
import com.processpuzzle.document.domain.PublishedDocumentRepository;
import com.processpuzzle.document.usecase.exception.DocumentNotFoundException;
import com.processpuzzle.document.usecase.exception.DocumentPublishingConflictException;
import com.processpuzzle.document.usecase.exception.DocumentTranslationNotFoundException;
import com.processpuzzle.document.usecase.service.DocumentGuard;
import com.processpuzzle.document.usecase.service.DocumentTranslationAssembler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Abandons unpublished edits by copying the published snapshot back over the draft.
 *
 * <p>Refused when the translation has never been published: there would be nothing to revert to,
 * and the obvious-looking alternative — emptying the draft — would destroy the only copy of the
 * content under a name that sounds like it undoes something small.
 */
@Service
@Transactional
public class DiscardDocumentDraft {

    private final DocumentRepository repository;
    private final DocumentDraftRepository draftRepository;
    private final PublishedDocumentRepository publishedRepository;
    private final DocumentTranslationAssembler assembler;
    private final DocumentGuard guard;

    public DiscardDocumentDraft(DocumentRepository repository,
                                DocumentDraftRepository draftRepository,
                                PublishedDocumentRepository publishedRepository,
                                DocumentTranslationAssembler assembler,
                                DocumentGuard guard) {
        this.repository = repository;
        this.draftRepository = draftRepository;
        this.publishedRepository = publishedRepository;
        this.assembler = assembler;
        this.guard = guard;
    }

    public DocumentTranslationView execute(String orgKey, String documentId, String locale) {
        Document document = repository.findByOrgKeyAndId(orgKey, documentId)
                .orElseThrow(() -> new DocumentNotFoundException(orgKey, documentId));
        guard.requirePublisher(document);

        DocumentDraft draft = draftRepository.findByOrgKeyAndDocumentIdAndLocale(orgKey, documentId, locale)
                .orElseThrow(() -> new DocumentTranslationNotFoundException(orgKey, documentId, locale));
        PublishedDocument snapshot = publishedRepository
                .findByOrgKeyAndDocumentIdAndLocale(orgKey, documentId, locale)
                .orElseThrow(() -> DocumentPublishingConflictException.nothingToRevertTo(documentId, locale));

        draft.revertTo(snapshot.getContent(), snapshot.getPublishedRevision());
        DocumentDraft reverted = draftRepository.save(draft);

        return DocumentTranslationView.ofDraft(reverted, snapshot, assembler.sourceRevisionOf(document));
    }
}
