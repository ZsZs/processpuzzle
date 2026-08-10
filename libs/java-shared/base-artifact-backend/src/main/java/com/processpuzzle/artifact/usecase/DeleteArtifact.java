package com.processpuzzle.artifact.usecase;

import com.processpuzzle.artifact.domain.ArtifactRepository;
import com.processpuzzle.artifact.usecase.exception.ArtifactNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DeleteArtifact {

    private final ArtifactRepository repository;

    public DeleteArtifact(ArtifactRepository repository) {
        this.repository = repository;
    }

    public void execute(String orgKey, String artifactId) {
        if (!repository.existsByOrgKeyAndId(orgKey, artifactId)) {
            throw new ArtifactNotFoundException(orgKey, artifactId);
        }
        repository.deleteById(new com.processpuzzle.artifact.domain.ArtifactKey(orgKey, artifactId));
    }
}
