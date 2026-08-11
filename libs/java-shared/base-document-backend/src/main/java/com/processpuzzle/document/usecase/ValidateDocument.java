package com.processpuzzle.document.usecase;

import com.processpuzzle.document.adapter.inbound.DocumentMapper;
import com.processpuzzle.document.domain.DocumentGraph;
import com.processpuzzle.document.model.DocumentInput;
import com.processpuzzle.document.usecase.service.DocumentReferentialIntegrityChecker;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ValidateDocument {

    private final DocumentReferentialIntegrityChecker integrityChecker;
    private final DocumentMapper mapper;

    public ValidateDocument(DocumentReferentialIntegrityChecker integrityChecker, DocumentMapper mapper) {
        this.integrityChecker = integrityChecker;
        this.mapper = mapper;
    }

    /**
     * Stateless and read-only by construction — no repository dependency at all, since a
     * candidate document need not exist yet. {@code CreateDocument}/{@code UpdateDocument}/the
     * block use cases call the same {@link DocumentReferentialIntegrityChecker} directly rather
     * than through this use case, so "valid enough to persist" and "valid enough to preview"
     * stay a single source of truth without an extra hop.
     */
    public ValidationOutcome execute(DocumentInput input) {
        DocumentGraph graph = mapper.toGraph(input);
        List<DocumentValidationProblem> problems = integrityChecker.check(graph);
        boolean valid = DocumentValidationProblem.blocking(problems).isEmpty();
        return new ValidationOutcome(valid, problems);
    }

    public record ValidationOutcome(boolean valid, List<DocumentValidationProblem> problems) {
    }
}
