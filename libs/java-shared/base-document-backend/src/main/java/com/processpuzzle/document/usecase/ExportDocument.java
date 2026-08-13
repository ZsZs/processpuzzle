package com.processpuzzle.document.usecase;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.processpuzzle.document.adapter.inbound.DocumentMapper;
import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentDraft;
import com.processpuzzle.document.domain.DocumentDraftRepository;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.model.DocumentInput;
import com.processpuzzle.document.usecase.exception.DocumentNotFoundException;
import com.processpuzzle.document.usecase.service.DocumentGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Exports a document with every translation's <em>draft</em> content — an export is a copy of the
 * source of truth, and the draft is what an editor would keep working on after re-importing it.
 * Publication state is deliberately absent from the file; see {@link ImportDocuments}.
 *
 * <p>Requires an editor, not merely a reader: an export is the whole document including
 * unpublished content and the role lists, so it must not be reachable by someone who may only read
 * what is published.
 */
@Service
@Transactional(readOnly = true)
public class ExportDocument {

    private final DocumentRepository repository;
    private final DocumentDraftRepository draftRepository;
    private final DocumentGuard guard;
    private final DocumentMapper mapper;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    public ExportDocument(DocumentRepository repository,
                          DocumentDraftRepository draftRepository,
                          DocumentGuard guard,
                          DocumentMapper mapper) {
        this.repository = repository;
        this.draftRepository = draftRepository;
        this.guard = guard;
        this.mapper = mapper;
    }

    public String execute(String orgKey, String documentId) {
        Document document = repository.findByOrgKeyAndId(orgKey, documentId)
                .orElseThrow(() -> new DocumentNotFoundException(orgKey, documentId));
        guard.requireEditor(document);

        List<DocumentDraft> drafts = draftRepository.findByOrgKeyAndDocumentId(orgKey, documentId);
        DocumentInput input = mapper.toInput(document, drafts);
        try {
            return yamlMapper.writeValueAsString(new DocumentYamlFile(List.of(input)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize document '" + documentId + "'", e);
        }
    }
}
