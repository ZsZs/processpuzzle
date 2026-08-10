package com.processpuzzle.artifact.usecase;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.processpuzzle.artifact.adapter.inbound.ArtifactMapper;
import com.processpuzzle.artifact.domain.Artifact;
import com.processpuzzle.artifact.domain.ArtifactRepository;
import com.processpuzzle.artifact.model.ArtifactInput;
import com.processpuzzle.artifact.usecase.exception.ArtifactNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ExportArtifact {

    private final ArtifactRepository repository;
    private final ArtifactMapper mapper;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    public ExportArtifact(ArtifactRepository repository, ArtifactMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public String execute(String orgKey, String artifactId) {
        Artifact artifact = repository.findByOrgKeyAndId(orgKey, artifactId)
                .orElseThrow(() -> new ArtifactNotFoundException(orgKey, artifactId));
        ArtifactInput input = mapper.toInput(artifact);
        try {
            return yamlMapper.writeValueAsString(new ArtifactImportDocument(java.util.List.of(input)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize artifact '" + artifactId + "'", e);
        }
    }
}
