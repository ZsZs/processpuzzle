package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentDraft;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.domain.PublishedDocument;
import com.processpuzzle.document.domain.PublishedDocumentRepository;
import com.processpuzzle.document.domain.event.DocumentPublished;
import com.processpuzzle.document.usecase.exception.DocumentNotFoundException;
import com.processpuzzle.document.usecase.exception.DocumentPublishingConflictException;
import com.processpuzzle.document.usecase.exception.DocumentTranslationNotFoundException;
import com.processpuzzle.document.usecase.service.DocumentGuard;
import com.processpuzzle.document.usecase.service.DocumentReferentialIntegrityChecker;
import com.processpuzzle.document.usecase.service.DocumentTranslationAssembler;
import com.processpuzzle.document.domain.DocumentDraftRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Copies one locale's draft into its published snapshot — the single point at which content becomes
 * visible to readers.
 *
 * <p>Two things it deliberately does not do. It does not touch the draft's {@code revision}: the
 * snapshot records which revision it was taken from, and moving the counter would make a freshly
 * published translation immediately report unpublished edits. And it does not move the document's
 * {@code publishedAt} on a republish — that field is the document's publication date, set once.
 */
@Service
@Transactional
public class PublishDocumentTranslation {

    private final DocumentRepository repository;
    private final DocumentDraftRepository draftRepository;
    private final PublishedDocumentRepository publishedRepository;
    private final DocumentReferentialIntegrityChecker integrityChecker;
    private final DocumentTranslationAssembler assembler;
    private final DocumentGuard guard;
    private final ApplicationEventPublisher events;

    public PublishDocumentTranslation(DocumentRepository repository,
                                      DocumentDraftRepository draftRepository,
                                      PublishedDocumentRepository publishedRepository,
                                      DocumentReferentialIntegrityChecker integrityChecker,
                                      DocumentTranslationAssembler assembler,
                                      DocumentGuard guard,
                                      ApplicationEventPublisher events) {
        this.repository = repository;
        this.draftRepository = draftRepository;
        this.publishedRepository = publishedRepository;
        this.integrityChecker = integrityChecker;
        this.assembler = assembler;
        this.guard = guard;
        this.events = events;
    }

    public DocumentTranslationView execute(String orgKey, String documentId, String locale) {
        Document document = repository.findByOrgKeyAndId(orgKey, documentId)
                .orElseThrow(() -> new DocumentNotFoundException(orgKey, documentId));
        guard.requirePublisher(document);

        DocumentDraft draft = draftRepository.findByOrgKeyAndDocumentIdAndLocale(orgKey, documentId, locale)
                .orElseThrow(() -> new DocumentTranslationNotFoundException(orgKey, documentId, locale));

        List<DocumentValidationProblem> blocking = DocumentValidationProblem.blocking(
                integrityChecker.check(document.getPorts(), draft.getContent()));
        if (!blocking.isEmpty()) {
            throw DocumentPublishingConflictException.notPublishable(documentId, locale, blocking);
        }

        Instant now = Instant.now();
        String principal = guard.currentPrincipal();
        PublishedDocument snapshot = publishedRepository
                .findByOrgKeyAndDocumentIdAndLocale(orgKey, documentId, locale)
                .orElseGet(() -> new PublishedDocument(orgKey, documentId, locale,
                        draft.getContent(), draft.getRevision(), now, principal));
        snapshot.replaceSnapshot(draft.getContent(), draft.getRevision(), now, principal);
        publishedRepository.save(snapshot);

        document.markFirstPublication(now);
        repository.save(document);

        events.publishEvent(new DocumentPublished(
                orgKey, documentId, document.getSlug(), locale, draft.getRevision(), now, principal));

        return DocumentTranslationView.ofDraft(draft, snapshot, assembler.sourceRevisionOf(document));
    }
}
