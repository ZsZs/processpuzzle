package com.processpuzzle.document.usecase;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.processpuzzle.document.adapter.inbound.DocumentMapper;
import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.model.DocumentInput;
import com.processpuzzle.document.usecase.service.DocumentReferentialIntegrityChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Simpler than {@code ImportRules}: {@link DocumentInput} already deserializes straight from
 * YAML with Jackson's YAML factory — no intermediate {@code *YamlEntry} DTO is needed the way
 * rules use one, since a document has no cross-entry linkage comparable to {@code extendsRuleId}
 * to resolve during import. All-or-nothing: the whole file is validated before anything is
 * persisted, same guarantee the contract promises.
 */
@Service
public class ImportDocuments {

    private final DocumentRepository repository;
    private final DocumentReferentialIntegrityChecker integrityChecker;
    private final DocumentMapper mapper;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    public ImportDocuments(DocumentRepository repository,
                            DocumentReferentialIntegrityChecker integrityChecker,
                            DocumentMapper mapper) {
        this.repository = repository;
        this.integrityChecker = integrityChecker;
        this.mapper = mapper;
    }

    @Transactional
    public ImportOutcome execute(String orgKey, InputStream input) throws IOException {
        DocumentYamlFile yamlFile = yamlMapper.readValue(input, DocumentYamlFile.class);
        List<DocumentInput> entries = yamlFile.documents() == null ? List.of() : yamlFile.documents();
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            DocumentInput entry = entries.get(i);
            if (entry.getId() == null || entry.getId().isBlank()) {
                errors.add("Entry " + i + " is missing 'id' and was skipped.");
                continue;
            }
            List<DocumentValidationProblem> blocking = DocumentValidationProblem.blocking(
                    integrityChecker.check(mapper.toGraph(entry)));
            if (!blocking.isEmpty()) {
                errors.add("Entry '" + entry.getId() + "': " + blocking);
            }
        }
        if (!errors.isEmpty()) {
            return new ImportOutcome(0, 0, errors);
        }

        int created = 0;
        int updated = 0;
        for (DocumentInput entry : entries) {
            boolean exists = repository.existsByOrgKeyAndId(orgKey, entry.getId());
            Document document = mapper.toDomain(orgKey, entry);
            if (exists) {
                Document existing = repository.findByOrgKeyAndId(orgKey, entry.getId()).orElseThrow();
                existing.replace(document.getTitle(), document.getDescription(), document.getGraph());
                repository.save(existing);
                updated++;
            } else {
                repository.save(document);
                created++;
            }
        }
        return new ImportOutcome(created, updated, List.of());
    }
}
