package com.processpuzzle.document.usecase;

import com.processpuzzle.document.adapter.inbound.DocumentMapper;
import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentContent;
import com.processpuzzle.document.domain.DocumentDraft;
import com.processpuzzle.document.domain.DocumentDraftRepository;
import com.processpuzzle.document.domain.DocumentPorts;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.model.DocumentInput;
import com.processpuzzle.document.model.DocumentTranslationInput;
import com.processpuzzle.document.usecase.exception.DocumentNotFoundException;
import com.processpuzzle.document.usecase.exception.DocumentSlugAlreadyExistsException;
import com.processpuzzle.document.usecase.exception.DocumentTranslationNotFoundException;
import com.processpuzzle.document.usecase.service.DocumentGuard;
import com.processpuzzle.document.usecase.service.DocumentReferentialIntegrityChecker;
import com.processpuzzle.document.usecase.service.DocumentTranslationAssembler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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
    private final DocumentReferentialIntegrityChecker integrityChecker;
    private final DocumentTranslationAssembler assembler;
    private final DocumentGuard guard;
    private final DocumentMapper mapper;

    public UpdateDocument(DocumentRepository repository,
                          DocumentDraftRepository draftRepository,
                          DocumentReferentialIntegrityChecker integrityChecker,
                          DocumentTranslationAssembler assembler,
                          DocumentGuard guard,
                          DocumentMapper mapper) {
        this.repository = repository;
        this.draftRepository = draftRepository;
        this.integrityChecker = integrityChecker;
        this.assembler = assembler;
        this.guard = guard;
        this.mapper = mapper;
    }

    public DocumentDetails execute(String orgKey, String documentId, DocumentInput input) {
        Document document = repository.findByOrgKeyAndId(orgKey, documentId)
                .orElseThrow(() -> new DocumentNotFoundException(orgKey, documentId));
        guard.requireEditor(document);

        if (!document.getSlug().equals(input.getSlug()) && repository.existsByOrgKeyAndSlug(orgKey, input.getSlug())) {
            throw new DocumentSlugAlreadyExistsException(orgKey, input.getSlug());
        }

        DocumentPorts newPorts = mapper.toPorts(input);
        Map<String, DocumentDraft> draftsByLocale = new HashMap<>();
        for (DocumentDraft draft : draftRepository.findByOrgKeyAndDocumentId(orgKey, documentId)) {
            draftsByLocale.put(draft.getLocale(), draft);
        }

        // Validate the candidate state of every locale — the ones being replaced with their new
        // content, the ones being left alone against the new ports — before writing anything.
        Map<String, DocumentContent> replacements = replacementsOf(input, draftsByLocale, orgKey, documentId);
        List<DocumentValidationProblem> problems = new ArrayList<>();
        for (DocumentDraft draft : draftsByLocale.values()) {
            DocumentContent candidate = replacements.getOrDefault(draft.getLocale(), draft.getContent());
            problems.addAll(DocumentValidationProblem.withLocale(
                    integrityChecker.check(newPorts, candidate), draft.getLocale()));
        }
        List<DocumentValidationProblem> blocking = DocumentValidationProblem.blocking(problems);
        if (!blocking.isEmpty()) {
            throw new IllegalArgumentException("Invalid document: " + blocking);
        }

        document.replaceProperties(
                input.getSlug(),
                input.getTitle(),
                input.getSubject(),
                input.getDescription(),
                input.getAuthor(),
                input.getSourceLocale(),
                Boolean.TRUE.equals(input.getIsPublic()),
                mapper.toRoles(input),
                newPorts);
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
    private Map<String, DocumentContent> replacementsOf(DocumentInput input,
                                                        Map<String, DocumentDraft> draftsByLocale,
                                                        String orgKey,
                                                        String documentId) {
        Map<String, DocumentContent> replacements = new HashMap<>();
        if (input.getTranslations() == null) {
            return replacements;
        }
        for (DocumentTranslationInput translation : input.getTranslations()) {
            DocumentContent content = mapper.toContentOrNull(translation);
            if (content == null) {
                continue;
            }
            if (!draftsByLocale.containsKey(translation.getLocale())) {
                throw new DocumentTranslationNotFoundException(orgKey, documentId, translation.getLocale());
            }
            replacements.put(translation.getLocale(), content);
        }
        return replacements;
    }
}
