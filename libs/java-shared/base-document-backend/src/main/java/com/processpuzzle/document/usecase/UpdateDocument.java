package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentContent;
import com.processpuzzle.document.domain.DocumentDraft;
import com.processpuzzle.document.domain.DocumentDraftRepository;
import com.processpuzzle.document.domain.DocumentPorts;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.domain.PublishedDocumentRepository;
import com.processpuzzle.document.usecase.exception.DocumentNotFoundException;
import com.processpuzzle.document.usecase.exception.DocumentSlugAlreadyExistsException;
import com.processpuzzle.document.usecase.exception.DocumentTranslationNotFoundException;
import com.processpuzzle.document.usecase.service.DocumentGuard;
import com.processpuzzle.document.usecase.service.DocumentReferentialIntegrityChecker;
import com.processpuzzle.document.usecase.service.DocumentTranslationAssembler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Whole-document replace: the invariant properties, plus the draft block list of every translation
 * the request names. A translation the request omits is left alone — removing one is
 * {@code RemoveDocumentTranslation}, so an omission cannot silently delete a language.
 *
 * <p>Published snapshots are never written here. An edit lands in the draft and becomes visible to
 * readers only on publish, which holds for this endpoint exactly as it does for the block-level
 * ones.
 */
@Service
@Transactional
public class UpdateDocument {

    private final DocumentRepository repository;
    private final DocumentDraftRepository draftRepository;
    private final PublishedDocumentRepository publishedRepository;
    private final DocumentReferentialIntegrityChecker integrityChecker;
    private final DocumentTranslationAssembler assembler;
    private final DocumentGuard guard;

    public UpdateDocument(DocumentRepository repository,
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

    public DocumentDetails execute(String orgKey, String documentId, DocumentCommand input) {
        Document document = repository.findByOrgKeyAndId(orgKey, documentId)
                .orElseThrow(() -> new DocumentNotFoundException(orgKey, documentId));
        guard.requireEditor(document);

        DocumentProperties properties = input.properties();
        if (!document.getSlug().equals(properties.slug()) && repository.existsByOrgKeyAndSlug(orgKey, properties.slug())) {
            throw new DocumentSlugAlreadyExistsException(orgKey, properties.slug());
        }

        DocumentPorts newPorts = properties.ports();
        Map<String, DocumentDraft> draftsByLocale = new HashMap<>();
        for (DocumentDraft draft : draftRepository.findByOrgKeyAndDocumentId(orgKey, documentId)) {
            draftsByLocale.put(draft.getLocale(), draft);
        }

        // Validate the candidate state of every locale — the ones being replaced with their new
        // content, the ones being left alone against the new ports — before writing anything.
        Map<String, DocumentContent> replacements = replacementsOf(input, draftsByLocale, orgKey, documentId);
        List<DocumentValidationProblem> blocking = DocumentValidationProblem.blocking(
                integrityChecker.checkPortChange(newPorts, List.copyOf(draftsByLocale.values()), replacements,
                        publishedRepository.findByOrgKeyAndDocumentId(orgKey, documentId)));
        if (!blocking.isEmpty()) {
            throw new IllegalArgumentException("Invalid document: " + blocking);
        }

        properties.applyTo(document);
        Document saved = repository.save(document);

        replacements.forEach((locale, content) -> {
            DocumentDraft draft = draftsByLocale.get(locale);
            draft.replaceBlocks(content.blocks());
            draftRepository.save(draft);
        });

        return new DocumentDetails(saved,
                assembler.contentOf(saved, saved.getSourceLocale(), true).orElse(null),
                assembler.statesOf(saved));
    }

    /**
     * The locales this request replaces content for, mapped to their new content. A named locale
     * that has no translation is an error rather than an implicit create: creating one has to decide
     * which source revision it is based on, which is {@code AddDocumentTranslation}'s job.
     */
    private Map<String, DocumentContent> replacementsOf(DocumentCommand input,
                                                        Map<String, DocumentDraft> draftsByLocale,
                                                        String orgKey,
                                                        String documentId) {
        Map<String, DocumentContent> replacements = new HashMap<>();
        for (DocumentTranslationCommand translation : input.translations()) {
            DocumentContent content = translation.content();
            if (content == null) {
                continue;
            }
            if (!draftsByLocale.containsKey(translation.locale())) {
                throw new DocumentTranslationNotFoundException(orgKey, documentId, translation.locale());
            }
            replacements.put(translation.locale(), content);
        }
        return replacements;
    }
}
