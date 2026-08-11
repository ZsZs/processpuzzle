package com.processpuzzle.document.usecase;

import com.processpuzzle.document.adapter.inbound.DocumentMapper;
import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentContent;
import com.processpuzzle.document.domain.DocumentDraft;
import com.processpuzzle.document.domain.DocumentDraftRepository;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.model.DocumentTranslationInput;
import com.processpuzzle.document.usecase.exception.DocumentNotFoundException;
import com.processpuzzle.document.usecase.exception.DocumentTranslationAlreadyExistsException;
import com.processpuzzle.document.usecase.service.DocumentGuard;
import com.processpuzzle.document.usecase.service.DocumentTranslationAssembler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Starts a translation in a new locale, as an unpublished draft.
 *
 * <p>When the request carries no blocks the source locale's current draft is copied as a starting
 * point, which is what a translator wants far more often than a blank page — and it makes the
 * copied block ids line up with the source's, which is what
 * {@code DocumentReferentialIntegrityChecker.checkWidgetCoverage} compares later. An explicit empty
 * array still means "start blank"; see {@code DocumentMapper.toContentOrNull} for why that
 * distinction survives deserialization.
 *
 * <p>{@code basedOnRevision} is recorded either way. Even a from-scratch translation was written
 * against some state of the original, and recording which one is what lets it be reported stale
 * later.
 */
@Service
@Transactional
public class AddDocumentTranslation {

    private final DocumentRepository repository;
    private final DocumentDraftRepository draftRepository;
    private final DocumentTranslationAssembler assembler;
    private final DocumentGuard guard;
    private final DocumentMapper mapper;

    public AddDocumentTranslation(DocumentRepository repository,
                                  DocumentDraftRepository draftRepository,
                                  DocumentTranslationAssembler assembler,
                                  DocumentGuard guard,
                                  DocumentMapper mapper) {
        this.repository = repository;
        this.draftRepository = draftRepository;
        this.assembler = assembler;
        this.guard = guard;
        this.mapper = mapper;
    }

    public DocumentTranslationView execute(String orgKey, String documentId, DocumentTranslationInput input) {
        Document document = repository.findByOrgKeyAndId(orgKey, documentId)
                .orElseThrow(() -> new DocumentNotFoundException(orgKey, documentId));
        guard.requireEditor(document);

        String locale = input.getLocale();
        if (draftRepository.existsByOrgKeyAndDocumentIdAndLocale(orgKey, documentId, locale)) {
            throw new DocumentTranslationAlreadyExistsException(documentId, locale);
        }

        Optional<DocumentDraft> source = draftRepository
                .findByOrgKeyAndDocumentIdAndLocale(orgKey, documentId, document.getSourceLocale());
        DocumentContent requested = mapper.toContentOrNull(input);
        DocumentContent content = requested != null
                ? requested
                : source.map(DocumentDraft::getContent).orElse(DocumentContent.empty());
        Long basedOnRevision = source.map(DocumentDraft::getRevision).orElse(null);

        DocumentDraft draft = draftRepository.save(
                new DocumentDraft(orgKey, documentId, locale, content, basedOnRevision));
        return DocumentTranslationView.ofDraft(draft, null, assembler.sourceRevisionOf(document));
    }
}
