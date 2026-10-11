package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentDraft;
import com.processpuzzle.document.domain.DocumentDraftRepository;
import com.processpuzzle.document.domain.DocumentPorts;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.domain.PublishedDocumentRepository;
import com.processpuzzle.document.usecase.exception.DocumentNotFoundException;
import com.processpuzzle.document.usecase.exception.DocumentSlugAlreadyExistsException;
import com.processpuzzle.document.usecase.service.DocumentGuard;
import com.processpuzzle.document.usecase.service.DocumentReferentialIntegrityChecker;
import com.processpuzzle.document.usecase.service.DocumentTranslationAssembler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * The language-invariant half only — what the generic Properties form saves. Content is untouched
 * because it is not on this entity at all: {@link DocumentProperties} has no translations
 * field, so a Properties save racing a Tiptap autosave cannot revert it. That guarantee is
 * structural rather than something this class has to uphold.
 *
 * <p>Still validates every translation, because changing the ports alone can invalidate content
 * nobody touched: every WIDGET block's {@code inputBindings}/{@code outputBindings} value has to
 * name a declared port, so deleting a port here orphans the bindings pointing at it — in every
 * language at once, which is exactly why the ports are invariant and the check has to sweep all of
 * them rather than just the locale the editor happens to be looking at.
 */
@Service
@Transactional
public class UpdateDocumentProperties {

    private final DocumentRepository repository;
    private final DocumentDraftRepository draftRepository;
    private final PublishedDocumentRepository publishedRepository;
    private final DocumentReferentialIntegrityChecker integrityChecker;
    private final DocumentTranslationAssembler assembler;
    private final DocumentGuard guard;

    public UpdateDocumentProperties(DocumentRepository repository,
                                    DocumentDraftRepository draftRepository,
                                    PublishedDocumentRepository publishedRepository,
                                    DocumentReferentialIntegrityChecker integrityChecker,
                                    DocumentTranslationAssembler assembler,
                                    DocumentGuard guard) {
        this.repository = repository;
        this.draftRepository = draftRepository;
        this.publishedRepository = publishedRepository;
        this.integrityChecker = integrityChecker;
        this.assembler = assembler;
        this.guard = guard;
    }

    public DocumentDetails execute(String orgKey, String documentId, DocumentProperties input) {
        Document document = repository.findByOrgKeyAndId(orgKey, documentId)
                .orElseThrow(() -> new DocumentNotFoundException(orgKey, documentId));
        guard.requireEditor(document);

        if (!document.getSlug().equals(input.slug()) && repository.existsByOrgKeyAndSlug(orgKey, input.slug())) {
            throw new DocumentSlugAlreadyExistsException(orgKey, input.slug());
        }

        DocumentPorts newPorts = input.ports();
        List<DocumentDraft> drafts = draftRepository.findByOrgKeyAndDocumentId(orgKey, documentId);
        List<DocumentValidationProblem> blocking = DocumentValidationProblem.blocking(
                integrityChecker.checkPortChange(newPorts, drafts, Map.of(),
                        publishedRepository.findByOrgKeyAndDocumentId(orgKey, documentId)));
        if (!blocking.isEmpty()) {
            throw new IllegalArgumentException("Invalid document properties: " + blocking);
        }

        input.applyTo(document);

        Document saved = repository.save(document);
        return new DocumentDetails(saved,
                assembler.contentOf(saved, saved.getSourceLocale(), true).orElse(null),
                assembler.statesOf(saved));
    }
}
