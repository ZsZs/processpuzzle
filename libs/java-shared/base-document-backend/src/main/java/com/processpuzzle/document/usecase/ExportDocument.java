package com.processpuzzle.document.usecase;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.processpuzzle.document.adapter.inbound.DocumentMapper;
import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.model.DocumentInput;
import com.processpuzzle.document.usecase.exception.DocumentNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ExportDocument {

    private final DocumentRepository repository;
    private final DocumentMapper mapper;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    public ExportDocument(DocumentRepository repository, DocumentMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public String execute(String orgKey, String documentId) {
        Document document = repository.findByOrgKeyAndId(orgKey, documentId)
                .orElseThrow(() -> new DocumentNotFoundException(orgKey, documentId));
        DocumentInput input = mapper.toInput(document);
        try {
            return yamlMapper.writeValueAsString(new DocumentYamlFile(java.util.List.of(input)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize document '" + documentId + "'", e);
        }
    }
}
