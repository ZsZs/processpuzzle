package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.usecase.exception.DocumentNotFoundException;
import com.processpuzzle.document.usecase.service.DocumentGuard;
import com.processpuzzle.document.usecase.service.DocumentTranslationAssembler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The design-side read. Requesting the draft additionally requires an editor role — this is the
 * only authenticated path that can return unpublished content, so the check lives here rather than
 * being inferred from the caller.
 */
@Service
@Transactional(readOnly = true)
public class FindDocument {

    private final DocumentRepository repository;
    private final DocumentTranslationAssembler assembler;
    private final DocumentGuard guard;

    public FindDocument(DocumentRepository repository, DocumentTranslationAssembler assembler, DocumentGuard guard) {
        this.repository = repository;
        this.assembler = assembler;
        this.guard = guard;
    }

    /**
     * @param locale the translation to return content for; the document's {@code sourceLocale} when null
     * @param draft  true to return editable content instead of the published snapshot
     */
    public DocumentDetails execute(String orgKey, String documentId, String locale, boolean draft) {
        Document document = repository.findByOrgKeyAndId(orgKey, documentId)
                .orElseThrow(() -> new DocumentNotFoundException(orgKey, documentId));

        if (draft) {
            guard.requireEditor(document);
        } else if (!guard.canRead(document)) {
            // 404 rather than 403: see DocumentAccessDeniedException for why a reader who may not
            // see a document is not told that it exists.
            throw new DocumentNotFoundException(orgKey, documentId);
        }

        String effectiveLocale = locale == null ? document.getSourceLocale() : locale;
        return new DocumentDetails(document,
                assembler.contentOf(document, effectiveLocale, draft).orElse(null),
                assembler.statesOf(document));
    }
}
