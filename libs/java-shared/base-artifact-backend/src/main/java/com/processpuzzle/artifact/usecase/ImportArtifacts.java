package com.processpuzzle.artifact.usecase;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.processpuzzle.artifact.adapter.inbound.ArtifactMapper;
import com.processpuzzle.artifact.domain.Artifact;
import com.processpuzzle.artifact.domain.ArtifactRepository;
import com.processpuzzle.artifact.model.ArtifactInput;
import com.processpuzzle.artifact.usecase.service.ArtifactReferentialIntegrityChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Simpler than {@code ImportRules}: {@link ArtifactInput} already deserializes straight from
 * YAML with Jackson's YAML factory — no intermediate {@code *YamlEntry} DTO is needed the way
 * rules use one, since an artifact has no cross-entry linkage comparable to {@code extendsRuleId}
 * to resolve during import. All-or-nothing: the whole file is validated before anything is
 * persisted, same guarantee the contract promises.
 */
@Service
public class ImportArtifacts {

    private final ArtifactRepository repository;
    private final ArtifactReferentialIntegrityChecker integrityChecker;
    private final ArtifactMapper mapper;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    public ImportArtifacts(ArtifactRepository repository,
                            ArtifactReferentialIntegrityChecker integrityChecker,
                            ArtifactMapper mapper) {
        this.repository = repository;
        this.integrityChecker = integrityChecker;
        this.mapper = mapper;
    }

    @Transactional
    public ImportOutcome execute(String orgKey, InputStream input) throws IOException {
        ArtifactImportDocument document = yamlMapper.readValue(input, ArtifactImportDocument.class);
        List<ArtifactInput> entries = document.artifacts() == null ? List.of() : document.artifacts();
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            ArtifactInput entry = entries.get(i);
            if (entry.getId() == null || entry.getId().isBlank()) {
                errors.add("Entry " + i + " is missing 'id' and was skipped.");
                continue;
            }
            List<ArtifactValidationProblem> blocking = ArtifactValidationProblem.blocking(
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
        for (ArtifactInput entry : entries) {
            boolean exists = repository.existsByOrgKeyAndId(orgKey, entry.getId());
            Artifact artifact = mapper.toDomain(orgKey, entry);
            if (exists) {
                Artifact existing = repository.findByOrgKeyAndId(orgKey, entry.getId()).orElseThrow();
                existing.replace(artifact.getTitle(), artifact.getDescription(), artifact.getGraph());
                repository.save(existing);
                updated++;
            } else {
                repository.save(artifact);
                created++;
            }
        }
        return new ImportOutcome(created, updated, List.of());
    }
}
