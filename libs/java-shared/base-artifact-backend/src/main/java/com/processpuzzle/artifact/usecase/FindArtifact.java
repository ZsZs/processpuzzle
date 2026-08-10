package com.processpuzzle.artifact.usecase;

import com.processpuzzle.artifact.domain.Artifact;
import com.processpuzzle.artifact.domain.ArtifactRepository;
import com.processpuzzle.artifact.usecase.exception.ArtifactNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FindArtifact {

    private final ArtifactRepository repository;

    public FindArtifact(ArtifactRepository repository) {
        this.repository = repository;
    }

    public Artifact execute(String orgKey, String artifactId) {
        return repository.findByOrgKeyAndId(orgKey, artifactId)
                .orElseThrow(() -> new ArtifactNotFoundException(orgKey, artifactId));
    }
}
