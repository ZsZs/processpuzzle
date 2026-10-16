package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentDraft;
import com.processpuzzle.document.domain.DocumentDraftRepository;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.domain.DocumentStatus;
import com.processpuzzle.document.domain.PublishedDocument;
import com.processpuzzle.document.domain.PublishedDocumentRepository;
import com.processpuzzle.document.domain.event.DocumentUnpublished;
import com.processpuzzle.document.usecase.exception.DocumentNotFoundException;
import com.processpuzzle.document.usecase.exception.DocumentTranslationNotFoundException;
import com.processpuzzle.document.usecase.service.DocumentGuard;
import com.processpuzzle.document.usecase.service.DocumentPlatformFacts;
import com.processpuzzle.document.usecase.service.DocumentTranslationAssembler;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Withdraws one locale from public view by discarding its published snapshot. The draft is
 * untouched, so the content is not lost and can be republished.
 *
 * <p>Deleting the snapshot rather than flagging it is what keeps "withdrawn" from being a third
 * stored state that can disagree with the content: with no snapshot, {@code status} derives back to
 * DRAFT and the public path simply finds nothing.
 *
 * <p>Idempotent — unpublishing something already unpublished is not an error. The caller's intent
 * is "this must not be public", and it already is not.
 */
@Service
@Transactional
public class UnpublishDocumentTranslation {

    private final DocumentRepository repository;
    private final DocumentDraftRepository draftRepository;
    private final PublishedDocumentRepository publishedRepository;
    private final DocumentTranslationAssembler assembler;
    private final DocumentGuard guard;
    private final ApplicationEventPublisher events;
    private final DocumentPlatformFacts facts;

    public UnpublishDocumentTranslation(DocumentRepository repository,
                                        DocumentDraftRepository draftRepository,
                                        PublishedDocumentRepository publishedRepository,
                                        DocumentTranslationAssembler assembler,
                                        DocumentGuard guard,
                                        ApplicationEventPublisher events,
                                        DocumentPlatformFacts facts) {
        this.repository = repository;
        this.draftRepository = draftRepository;
        this.publishedRepository = publishedRepository;
        this.assembler = assembler;
        this.guard = guard;
        this.events = events;
        this.facts = facts;
    }

    public DocumentTranslationView execute(String orgKey, String documentId, String locale) {
        Document document = repository.findByOrgKeyAndId(orgKey, documentId)
                .orElseThrow(() -> new DocumentNotFoundException(orgKey, documentId));
        guard.requirePublisher(document);

        DocumentDraft draft = draftRepository.findByOrgKeyAndDocumentIdAndLocale(orgKey, documentId, locale)
                .orElseThrow(() -> new DocumentTranslationNotFoundException(orgKey, documentId, locale));

        DocumentStatus before = locale.equals(document.getSourceLocale())
                ? DocumentStatus.derive(draft.getRevision(), publishedRepository
                        .findByOrgKeyAndDocumentIdAndLocale(orgKey, documentId, locale)
                        .map(PublishedDocument::getPublishedRevision).orElse(null))
                : null;
        publishedRepository.deleteByOrgKeyAndDocumentIdAndLocale(orgKey, documentId, locale);
        events.publishEvent(new DocumentUnpublished(
                orgKey, documentId, document.getSlug(), locale, Instant.now(), guard.currentPrincipal()));
        if (before != null) {
            facts.stateChanged(document, before, DocumentStatus.DRAFT);
        }

        return DocumentTranslationView.ofDraft(draft, null, assembler.sourceRevisionOf(document));
    }
}
