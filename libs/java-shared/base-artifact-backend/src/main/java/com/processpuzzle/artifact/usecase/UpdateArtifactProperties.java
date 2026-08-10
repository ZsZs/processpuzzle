package com.processpuzzle.artifact.usecase;

import com.processpuzzle.artifact.adapter.inbound.ArtifactMapper;
import com.processpuzzle.artifact.domain.Artifact;
import com.processpuzzle.artifact.domain.ArtifactGraph;
import com.processpuzzle.artifact.domain.ArtifactRepository;
import com.processpuzzle.artifact.model.ArtifactPropertiesInput;
import com.processpuzzle.artifact.usecase.exception.ArtifactNotFoundException;
import com.processpuzzle.artifact.usecase.service.ArtifactReferentialIntegrityChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Title, description and ports only — the blocks already stored are carried over untouched, which
 * is what lets the generic Properties form save without racing the block-level editing endpoints.
 * {@link ArtifactPropertiesInput} has no blocks field at all, so that guarantee is structural
 * rather than a convention this class has to uphold.
 *
 * <p>Still runs the full {@link ArtifactReferentialIntegrityChecker} pass, because changing the
 * ports alone can invalidate blocks nobody touched: every WIDGET block's
 * {@code inputBindings}/{@code outputBindings} value has to name a declared port, so deleting a
 * port here orphans the bindings pointing at it. Bumps the same {@code @Version} as
 * {@link UpdateArtifact} — both write the one Artifact row.
 */
@Service
@Transactional
public class UpdateArtifactProperties {

    private final ArtifactRepository repository;
    private final ArtifactReferentialIntegrityChecker integrityChecker;
    private final ArtifactMapper mapper;

    public UpdateArtifactProperties(ArtifactRepository repository,
                                     ArtifactReferentialIntegrityChecker integrityChecker,
                                     ArtifactMapper mapper) {
        this.repository = repository;
        this.integrityChecker = integrityChecker;
        this.mapper = mapper;
    }

    public Artifact execute(String orgKey, String artifactId, ArtifactPropertiesInput input) {
        Artifact artifact = repository.findByOrgKeyAndId(orgKey, artifactId)
                .orElseThrow(() -> new ArtifactNotFoundException(orgKey, artifactId));

        ArtifactGraph newGraph = mapper.toGraph(input, artifact.getGraph());
        List<ArtifactValidationProblem> blocking =
                ArtifactValidationProblem.blocking(integrityChecker.check(newGraph));
        if (!blocking.isEmpty()) {
            throw new IllegalArgumentException("Invalid artifact: " + blocking);
        }

        artifact.replace(input.getTitle(), input.getDescription(), newGraph);
        return repository.save(artifact);
    }
}
