package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentContent;
import com.processpuzzle.document.domain.DocumentDraft;
import com.processpuzzle.document.domain.DocumentDraftRepository;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.usecase.exception.DocumentSlugAlreadyExistsException;
import com.processpuzzle.document.usecase.service.DocumentGuard;
import com.processpuzzle.document.usecase.service.DocumentReferentialIntegrityChecker;
import com.processpuzzle.document.usecase.service.DocumentTranslationAssembler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Creates a document and the draft of its source locale, in one transaction. Nothing is published:
 * a new document is always editorial work in progress, and making it live is a separate, explicit
 * act.
 */
@Service
@Transactional
public class CreateDocument {

    private final DocumentRepository repository;
    private final DocumentDraftRepository draftRepository;
    private final DocumentReferentialIntegrityChecker integrityChecker;
    private final DocumentTranslationAssembler assembler;
    private final DocumentGuard guard;

    public CreateDocument(DocumentRepository repository,
                          DocumentDraftRepository draftRepository,
                          DocumentReferentialIntegrityChecker integrityChecker,
                          DocumentTranslationAssembler assembler,
                          DocumentGuard guard) {
        this.repository = repository;
        this.draftRepository = draftRepository;
        this.integrityChecker = integrityChecker;
        this.assembler = assembler;
        this.guard = guard;
    }

    public DocumentDetails execute(String orgKey, DocumentCommand input) {
        guard.requireOrganizationAccess(orgKey);

        // Identity is the server's to mint, never part of the command.
        String documentId = UUID.randomUUID().toString();
        if (repository.existsByOrgKeyAndSlug(orgKey, input.properties().slug())) {
            throw new DocumentSlugAlreadyExistsException(orgKey, input.properties().slug());
        }

        Document document = input.properties().create(orgKey, documentId, guard.currentPrincipal());
        DocumentContent sourceContent = sourceContentOf(input);

        List<DocumentValidationProblem> blocking = DocumentValidationProblem.blocking(
                integrityChecker.check(document.getPorts(), sourceContent));
        if (!blocking.isEmpty()) {
            throw new IllegalArgumentException("Invalid document: " + blocking);
        }

        Document saved = repository.save(document);
        // basedOnRevision is null: the source locale is based on nothing, which is also what keeps
        // it from ever reporting itself out of date.
        draftRepository.save(new DocumentDraft(orgKey, documentId, document.getSourceLocale(), sourceContent, null));

        return new DocumentDetails(saved,
                assembler.contentOf(saved, saved.getSourceLocale(), true).orElse(null),
                assembler.statesOf(saved));
    }

    /**
     * The blocks the request carried for the source locale, or none. Translations for other locales
     * in a create payload are ignored on purpose: a translation records the source revision it was
     * made from, and there is no meaningful revision to record until the source exists. Add them
     * with {@code addDocumentTranslation} once it does.
     */
    private DocumentContent sourceContentOf(DocumentCommand input) {
        return input.translations().stream()
                .filter(translation -> input.properties().sourceLocale().equals(translation.locale()))
                .findFirst()
                .map(DocumentTranslationCommand::contentOrEmpty)
                .orElse(DocumentContent.empty());
    }

}
