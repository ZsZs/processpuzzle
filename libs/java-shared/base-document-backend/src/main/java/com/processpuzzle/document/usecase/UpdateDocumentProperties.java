package com.processpuzzle.document.usecase;

import com.processpuzzle.document.adapter.inbound.DocumentMapper;
import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentDraft;
import com.processpuzzle.document.domain.DocumentDraftRepository;
import com.processpuzzle.document.domain.DocumentPorts;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.model.DocumentPropertiesInput;
import com.processpuzzle.document.usecase.exception.DocumentNotFoundException;
import com.processpuzzle.document.usecase.exception.DocumentSlugAlreadyExistsException;
import com.processpuzzle.document.usecase.service.DocumentGuard;
import com.processpuzzle.document.usecase.service.DocumentReferentialIntegrityChecker;
import com.processpuzzle.document.usecase.service.DocumentTranslationAssembler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * The language-invariant half only — what the generic Properties form saves. Content is untouched
 * because it is not on this entity at all: {@link DocumentPropertiesInput} has no translations
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
    private final DocumentReferentialIntegrityChecker integrityChecker;
    private final DocumentTranslationAssembler assembler;
    private final DocumentGuard guard;
    private final DocumentMapper mapper;

    public UpdateDocumentProperties(DocumentRepository repository,
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

    public DocumentDetails execute(String orgKey, String documentId, DocumentPropertiesInput input) {
        Document document = repository.findByOrgKeyAndId(orgKey, documentId)
                .orElseThrow(() -> new DocumentNotFoundException(orgKey, documentId));
        guard.requireEditor(document);

        if (!document.getSlug().equals(input.getSlug()) && repository.existsByOrgKeyAndSlug(orgKey, input.getSlug())) {
            throw new DocumentSlugAlreadyExistsException(orgKey, input.getSlug());
        }

        DocumentPorts newPorts = mapper.toPorts(input);
        List<DocumentDraft> drafts = draftRepository.findByOrgKeyAndDocumentId(orgKey, documentId);
        List<DocumentValidationProblem> problems = new ArrayList<>();
        for (DocumentDraft draft : drafts) {
            problems.addAll(DocumentValidationProblem.withLocale(
                    integrityChecker.check(newPorts, draft.getContent()), draft.getLocale()));
        }
        List<DocumentValidationProblem> blocking = DocumentValidationProblem.blocking(problems);
        if (!blocking.isEmpty()) {
            throw new IllegalArgumentException("Invalid document properties: " + blocking);
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
        return new DocumentDetails(saved,
                assembler.contentOf(saved, saved.getSourceLocale(), true).orElse(null),
                assembler.statesOf(saved));
    }
}
