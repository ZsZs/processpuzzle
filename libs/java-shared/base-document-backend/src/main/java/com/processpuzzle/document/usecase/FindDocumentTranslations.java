package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.usecase.exception.DocumentNotFoundException;
import com.processpuzzle.document.usecase.exception.DocumentTranslationNotFoundException;
import com.processpuzzle.document.usecase.service.DocumentGuard;
import com.processpuzzle.document.usecase.service.DocumentTranslationAssembler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The locale-selector and per-locale status reads: which languages a document exists in, and one
 * language's content on its own.
 */
@Service
@Transactional(readOnly = true)
public class FindDocumentTranslations {

    private final DocumentRepository repository;
    private final DocumentTranslationAssembler assembler;
    private final DocumentGuard guard;

    public FindDocumentTranslations(DocumentRepository repository,
                                    DocumentTranslationAssembler assembler,
                                    DocumentGuard guard) {
        this.repository = repository;
        this.assembler = assembler;
        this.guard = guard;
    }

    public List<DocumentTranslationView> executeAll(String orgKey, String documentId) {
        Document document = readableDocument(orgKey, documentId);
        return assembler.statesOf(document);
    }

    /** One locale's content. As in {@code FindDocument}, only an editor may ask for the draft. */
    public DocumentTranslationView executeOne(String orgKey, String documentId, String locale, boolean draft) {
        Document document = readableDocument(orgKey, documentId);
        if (draft) {
            guard.requireEditor(document);
        }
        return assembler.contentOf(document, locale, draft)
                .orElseThrow(() -> new DocumentTranslationNotFoundException(orgKey, documentId, locale));
    }

    private Document readableDocument(String orgKey, String documentId) {
        Document document = repository.findByOrgKeyAndId(orgKey, documentId)
                .orElseThrow(() -> new DocumentNotFoundException(orgKey, documentId));
        if (!guard.canRead(document)) {
            throw new DocumentNotFoundException(orgKey, documentId);
        }
        return document;
    }
}
