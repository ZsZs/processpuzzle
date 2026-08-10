package com.processpuzzle.artifact.usecase;

import com.processpuzzle.artifact.domain.Artifact;
import com.processpuzzle.artifact.domain.ArtifactBlock;
import com.processpuzzle.artifact.domain.ArtifactRepository;
import com.processpuzzle.artifact.usecase.exception.ArtifactNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class ReorderArtifactBlocks {

    private final ArtifactRepository repository;

    public ReorderArtifactBlocks(ArtifactRepository repository) {
        this.repository = repository;
    }

    /**
     * {@code blockIds} must be a permutation of the artifact's current block ids — no
     * position field exists to fall back on, so an omitted or added id is rejected outright
     * rather than silently dropping or ignoring blocks.
     */
    public List<ArtifactBlock> execute(String orgKey, String artifactId, List<String> blockIds) {
        Artifact artifact = repository.findByOrgKeyAndId(orgKey, artifactId)
                .orElseThrow(() -> new ArtifactNotFoundException(orgKey, artifactId));

        List<ArtifactBlock> current = artifact.getGraph().blocks();
        Map<String, ArtifactBlock> byId = current.stream()
                .collect(Collectors.toMap(ArtifactBlock::id, b -> b));

        Set<String> currentIds = new HashSet<>(byId.keySet());
        Set<String> requestedIds = new HashSet<>(blockIds);
        if (!currentIds.equals(requestedIds) || blockIds.size() != current.size()) {
            throw new IllegalArgumentException(
                    "blockIds must be an exact permutation of this artifact's current block ids");
        }

        List<ArtifactBlock> reordered = new ArrayList<>(blockIds.size());
        for (String id : blockIds) {
            reordered.add(byId.get(id));
        }

        artifact.replaceBlocks(reordered);
        repository.save(artifact);
        return reordered;
    }
}
