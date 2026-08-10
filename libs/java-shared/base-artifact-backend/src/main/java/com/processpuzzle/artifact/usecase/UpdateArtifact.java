package com.processpuzzle.artifact.usecase;

import com.processpuzzle.artifact.adapter.inbound.ArtifactMapper;
import com.processpuzzle.artifact.domain.Artifact;
import com.processpuzzle.artifact.domain.ArtifactGraph;
import com.processpuzzle.artifact.domain.ArtifactRepository;
import com.processpuzzle.artifact.model.ArtifactInput;
import com.processpuzzle.artifact.usecase.exception.ArtifactNotFoundException;
import com.processpuzzle.artifact.usecase.service.ArtifactReferentialIntegrityChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class UpdateArtifact {

    private final ArtifactRepository repository;
    private final ArtifactReferentialIntegrityChecker integrityChecker;
    private final ArtifactMapper mapper;

    public UpdateArtifact(ArtifactRepository repository,
                           ArtifactReferentialIntegrityChecker integrityChecker,
                           ArtifactMapper mapper) {
        this.repository = repository;
        this.integrityChecker = integrityChecker;
        this.mapper = mapper;
    }

    public Artifact execute(String orgKey, String artifactId, ArtifactInput input) {
        Artifact artifact = repository.findByOrgKeyAndId(orgKey, artifactId)
                .orElseThrow(() -> new ArtifactNotFoundException(orgKey, artifactId));

        ArtifactGraph newGraph = mapper.toGraph(input);
        List<ArtifactValidationProblem> blocking =
                ArtifactValidationProblem.blocking(integrityChecker.check(newGraph));
        if (!blocking.isEmpty()) {
            throw new IllegalArgumentException("Invalid artifact: " + blocking);
        }

        // No explicit version bump — @Version on Artifact.version handles optimistic locking
        // on flush, unlike AppDefinition.revision which is a plain column for draft/publish
        // reasons that don't apply here yet. Spring Data JPA raises
        // ObjectOptimisticLockingFailureException on a stale write; the exception handler
        // maps that to 409, same status the contract's Conflict response promises.
        artifact.replace(input.getTitle(), input.getDescription(), newGraph);
        return repository.save(artifact);
    }
}
