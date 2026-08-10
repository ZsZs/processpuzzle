package com.processpuzzle.artifact.usecase;

import com.processpuzzle.artifact.adapter.inbound.ArtifactMapper;
import com.processpuzzle.artifact.domain.Artifact;
import com.processpuzzle.artifact.domain.ArtifactRepository;
import com.processpuzzle.artifact.model.ArtifactInput;
import com.processpuzzle.artifact.usecase.exception.ArtifactAlreadyExistsException;
import com.processpuzzle.artifact.usecase.service.ArtifactReferentialIntegrityChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class CreateArtifact {

    private final ArtifactRepository repository;
    private final ArtifactReferentialIntegrityChecker integrityChecker;
    private final ArtifactMapper mapper;

    public CreateArtifact(ArtifactRepository repository,
                           ArtifactReferentialIntegrityChecker integrityChecker,
                           ArtifactMapper mapper) {
        this.repository = repository;
        this.integrityChecker = integrityChecker;
        this.mapper = mapper;
    }

    public Artifact execute(String orgKey, ArtifactInput input) {
        // Same rationale as CreateRule: an explicit existence check rather than relying on
        // save(), since save() merges on an assigned composite id and would silently
        // overwrite instead of conflicting.
        if (repository.existsByOrgKeyAndId(orgKey, input.getId())) {
            throw new ArtifactAlreadyExistsException(orgKey, input.getId());
        }

        Artifact artifact = mapper.toDomain(orgKey, input);
        List<ArtifactValidationProblem> problems = integrityChecker.check(artifact.getGraph());
        List<ArtifactValidationProblem> blocking = ArtifactValidationProblem.blocking(problems);
        if (!blocking.isEmpty()) {
            throw new IllegalArgumentException("Invalid artifact: " + blocking);
        }

        return repository.save(artifact);
    }
}
