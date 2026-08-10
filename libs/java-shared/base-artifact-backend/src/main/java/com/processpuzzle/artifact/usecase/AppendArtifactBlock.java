package com.processpuzzle.artifact.usecase;

import com.processpuzzle.artifact.adapter.inbound.ArtifactMapper;
import com.processpuzzle.artifact.domain.Artifact;
import com.processpuzzle.artifact.domain.ArtifactBlock;
import com.processpuzzle.artifact.domain.ArtifactGraph;
import com.processpuzzle.artifact.domain.ArtifactRepository;
import com.processpuzzle.artifact.model.ArtifactBlockInput;
import com.processpuzzle.artifact.usecase.exception.ArtifactNotFoundException;
import com.processpuzzle.artifact.usecase.service.ArtifactReferentialIntegrityChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class AppendArtifactBlock {

    private final ArtifactRepository repository;
    private final ArtifactReferentialIntegrityChecker integrityChecker;
    private final ArtifactMapper mapper;

    public AppendArtifactBlock(ArtifactRepository repository,
                                ArtifactReferentialIntegrityChecker integrityChecker,
                                ArtifactMapper mapper) {
        this.repository = repository;
        this.integrityChecker = integrityChecker;
        this.mapper = mapper;
    }

    public ArtifactBlock execute(String orgKey, String artifactId, ArtifactBlockInput input) {
        Artifact artifact = repository.findByOrgKeyAndId(orgKey, artifactId)
                .orElseThrow(() -> new ArtifactNotFoundException(orgKey, artifactId));

        ArtifactBlock newBlock = mapper.toBlock(UUID.randomUUID().toString(), input);
        List<ArtifactBlock> updated = new ArrayList<>(artifact.getGraph().blocks());
        updated.add(newBlock);
        ArtifactGraph candidate = artifact.getGraph().withBlocks(updated);

        List<ArtifactValidationProblem> blocking =
                ArtifactValidationProblem.blocking(integrityChecker.check(candidate));
        if (!blocking.isEmpty()) {
            throw new IllegalArgumentException("Invalid block: " + blocking);
        }

        artifact.replaceBlocks(updated);
        repository.save(artifact);
        return newBlock;
    }
}
