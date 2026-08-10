package com.processpuzzle.artifact.usecase;

import com.processpuzzle.artifact.domain.Artifact;
import com.processpuzzle.artifact.domain.ArtifactBlock;
import com.processpuzzle.artifact.domain.ArtifactRepository;
import com.processpuzzle.artifact.usecase.exception.ArtifactBlockNotFoundException;
import com.processpuzzle.artifact.usecase.exception.ArtifactBlockReferencedException;
import com.processpuzzle.artifact.usecase.exception.ArtifactNotFoundException;
import com.processpuzzle.artifact.usecase.service.ArtifactReferentialIntegrityChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class DeleteArtifactBlock {

    private final ArtifactRepository repository;
    private final ArtifactReferentialIntegrityChecker integrityChecker;

    public DeleteArtifactBlock(ArtifactRepository repository,
                                ArtifactReferentialIntegrityChecker integrityChecker) {
        this.repository = repository;
        this.integrityChecker = integrityChecker;
    }

    public void execute(String orgKey, String artifactId, String blockId) {
        Artifact artifact = repository.findByOrgKeyAndId(orgKey, artifactId)
                .orElseThrow(() -> new ArtifactNotFoundException(orgKey, artifactId));

        List<ArtifactBlock> blocks = artifact.getGraph().blocks();
        boolean exists = blocks.stream().anyMatch(b -> b.id().equals(blockId));
        if (!exists) {
            throw new ArtifactBlockNotFoundException(orgKey, artifactId, blockId);
        }

        // Referenced-by check runs against the *current* graph, before removal — deleting the
        // block first would make it invisible to its own referrers' childIds/widgetEmbed scan.
        List<String> referencingBlockIds = integrityChecker.referencesTo(artifact.getGraph(), blockId);
        if (!referencingBlockIds.isEmpty()) {
            throw new ArtifactBlockReferencedException(blockId, referencingBlockIds);
        }

        List<ArtifactBlock> remaining = new ArrayList<>(blocks);
        remaining.removeIf(b -> b.id().equals(blockId));
        artifact.replaceBlocks(remaining);
        repository.save(artifact);
    }
}
