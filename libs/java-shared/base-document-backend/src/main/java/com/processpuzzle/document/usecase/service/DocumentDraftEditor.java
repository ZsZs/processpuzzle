package com.processpuzzle.document.usecase.service;

import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentBlock;
import com.processpuzzle.document.domain.DocumentContent;
import com.processpuzzle.document.domain.DocumentDraft;
import com.processpuzzle.document.domain.DocumentDraftRepository;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.usecase.DocumentValidationProblem;
import com.processpuzzle.document.usecase.exception.DocumentNotFoundException;
import com.processpuzzle.document.usecase.exception.DocumentTranslationNotFoundException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The load-authorize-mutate-validate-save sequence every block-level use case performs, in one
 * place. Without it each of append, replace, delete and reorder repeats the same six steps, and the
 * one that matters most is the easiest to leave out: validating the <em>candidate</em> content
 * before it is stored, rather than the content that was already there.
 *
 * <p>Editing always targets the draft. There is no path here that writes a published snapshot —
 * that is {@code PublishDocumentTranslation}'s job alone, which is what makes "an edit is not
 * visible to readers until it is published" true by construction rather than by convention.
 */
@Component
public class DocumentDraftEditor {

    /** A block-list transformation. May throw to reject the edit outright. */
    public interface BlockMutation {
        List<DocumentBlock> apply(List<DocumentBlock> current);
    }

    private final DocumentRepository documentRepository;
    private final DocumentDraftRepository draftRepository;
    private final DocumentReferentialIntegrityChecker integrityChecker;
    private final DocumentGuard guard;

    public DocumentDraftEditor(DocumentRepository documentRepository,
                               DocumentDraftRepository draftRepository,
                               DocumentReferentialIntegrityChecker integrityChecker,
                               DocumentGuard guard) {
        this.documentRepository = documentRepository;
        this.draftRepository = draftRepository;
        this.integrityChecker = integrityChecker;
        this.guard = guard;
    }

    /**
     * Applies {@code mutation} to one locale's draft, rejecting the result if it does not satisfy
     * referential integrity against the document's ports.
     *
     * @return the saved draft, so a caller that needs the new revision does not have to reload
     */
    public DocumentDraft apply(String orgKey, String documentId, String locale, BlockMutation mutation) {
        Document document = requireEditableDocument(orgKey, documentId);
        DocumentDraft draft = requireDraft(orgKey, documentId, locale);

        List<DocumentBlock> candidate = mutation.apply(draft.getBlocks());
        List<DocumentValidationProblem> blocking = DocumentValidationProblem.blocking(
                integrityChecker.check(document.getPorts(), DocumentContent.of(candidate)));
        if (!blocking.isEmpty()) {
            throw new IllegalArgumentException("Invalid document content: " + blocking);
        }

        draft.replaceBlocks(candidate);
        return draftRepository.save(draft);
    }

    /** Loads a document and rejects the call unless the principal may edit it. */
    public Document requireEditableDocument(String orgKey, String documentId) {
        Document document = documentRepository.findByOrgKeyAndId(orgKey, documentId)
                .orElseThrow(() -> new DocumentNotFoundException(orgKey, documentId));
        guard.requireEditor(document);
        return document;
    }

    public DocumentDraft requireDraft(String orgKey, String documentId, String locale) {
        return draftRepository.findByOrgKeyAndDocumentIdAndLocale(orgKey, documentId, locale)
                .orElseThrow(() -> new DocumentTranslationNotFoundException(orgKey, documentId, locale));
    }
}
