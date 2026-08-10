package com.processpuzzle.artifact.usecase;

import com.processpuzzle.artifact.adapter.inbound.ArtifactMapper;
import com.processpuzzle.artifact.domain.Artifact;
import com.processpuzzle.artifact.domain.ArtifactBlock;
import com.processpuzzle.artifact.domain.ArtifactGraph;
import com.processpuzzle.artifact.domain.ArtifactRepository;
import com.processpuzzle.artifact.model.ArtifactBlockInput;
import com.processpuzzle.artifact.usecase.exception.ArtifactBlockNotFoundException;
import com.processpuzzle.artifact.usecase.exception.ArtifactNotFoundException;
import com.processpuzzle.artifact.usecase.service.ArtifactReferentialIntegrityChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class ReplaceArtifactBlock {

    private final ArtifactRepository repository;
    private final ArtifactReferentialIntegrityChecker integrityChecker;
    private final ArtifactMapper mapper;

    public ReplaceArtifactBlock(ArtifactRepository repository,
                                 ArtifactReferentialIntegrityChecker integrityChecker,
                                 ArtifactMapper mapper) {
        this.repository = repository;
        this.integrityChecker = integrityChecker;
        this.mapper = mapper;
    }

    public ArtifactBlock execute(String orgKey, String artifactId, String blockId, ArtifactBlockInput input) {
        Artifact artifact = repository.findByOrgKeyAndId(orgKey, artifactId)
                .orElseThrow(() -> new ArtifactNotFoundException(orgKey, artifactId));

        List<ArtifactBlock> blocks = new ArrayList<>(artifact.getGraph().blocks());
        int index = indexOf(blocks, blockId, orgKey, artifactId);

        ArtifactBlock replacement = mapper.toBlock(blockId, input);
        blocks.set(index, replacement);
        ArtifactGraph candidate = artifact.getGraph().withBlocks(blocks);

        List<ArtifactValidationProblem> blocking =
                ArtifactValidationProblem.blocking(integrityChecker.check(candidate));
        if (!blocking.isEmpty()) {
            throw new IllegalArgumentException("Invalid block: " + blocking);
        }

        artifact.replaceBlocks(blocks);
        repository.save(artifact);
        return replacement;
    }

    private static int indexOf(List<ArtifactBlock> blocks, String blockId, String orgKey, String artifactId) {
        for (int i = 0; i < blocks.size(); i++) {
            if (blocks.get(i).id().equals(blockId)) {
                return i;
            }
        }
        throw new ArtifactBlockNotFoundException(orgKey, artifactId, blockId);
    }
}
