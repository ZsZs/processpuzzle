package com.processpuzzle.artifact.usecase;

import com.processpuzzle.artifact.adapter.inbound.ArtifactMapper;
import com.processpuzzle.artifact.domain.ArtifactGraph;
import com.processpuzzle.artifact.model.ArtifactInput;
import com.processpuzzle.artifact.usecase.service.ArtifactReferentialIntegrityChecker;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ValidateArtifact {

    private final ArtifactReferentialIntegrityChecker integrityChecker;
    private final ArtifactMapper mapper;

    public ValidateArtifact(ArtifactReferentialIntegrityChecker integrityChecker, ArtifactMapper mapper) {
        this.integrityChecker = integrityChecker;
        this.mapper = mapper;
    }

    /**
     * Stateless and read-only by construction — no repository dependency at all, since a
     * candidate artifact need not exist yet. {@code CreateArtifact}/{@code UpdateArtifact}/the
     * block use cases call the same {@link ArtifactReferentialIntegrityChecker} directly rather
     * than through this use case, so "valid enough to persist" and "valid enough to preview"
     * stay a single source of truth without an extra hop.
     */
    public ValidationOutcome execute(ArtifactInput input) {
        ArtifactGraph graph = mapper.toGraph(input);
        List<ArtifactValidationProblem> problems = integrityChecker.check(graph);
        boolean valid = ArtifactValidationProblem.blocking(problems).isEmpty();
        return new ValidationOutcome(valid, problems);
    }

    public record ValidationOutcome(boolean valid, List<ArtifactValidationProblem> problems) {
    }
}
