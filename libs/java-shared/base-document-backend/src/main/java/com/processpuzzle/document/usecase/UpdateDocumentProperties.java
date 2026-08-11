package com.processpuzzle.document.usecase;

import com.processpuzzle.document.adapter.inbound.DocumentMapper;
import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentGraph;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.model.DocumentPropertiesInput;
import com.processpuzzle.document.usecase.exception.DocumentNotFoundException;
import com.processpuzzle.document.usecase.service.DocumentReferentialIntegrityChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Title, description and ports only — the blocks already stored are carried over untouched, which
 * is what lets the generic Properties form save without racing the block-level editing endpoints.
 * {@link DocumentPropertiesInput} has no blocks field at all, so that guarantee is structural
 * rather than a convention this class has to uphold.
 *
 * <p>Still runs the full {@link DocumentReferentialIntegrityChecker} pass, because changing the
 * ports alone can invalidate blocks nobody touched: every WIDGET block's
 * {@code inputBindings}/{@code outputBindings} value has to name a declared port, so deleting a
 * port here orphans the bindings pointing at it. Bumps the same {@code @Version} as
 * {@link UpdateDocument} — both write the one Document row.
 */
@Service
@Transactional
public class UpdateDocumentProperties {

    private final DocumentRepository repository;
    private final DocumentReferentialIntegrityChecker integrityChecker;
    private final DocumentMapper mapper;

    public UpdateDocumentProperties(DocumentRepository repository,
                                     DocumentReferentialIntegrityChecker integrityChecker,
                                     DocumentMapper mapper) {
        this.repository = repository;
        this.integrityChecker = integrityChecker;
        this.mapper = mapper;
    }

    public Document execute(String orgKey, String documentId, DocumentPropertiesInput input) {
        Document document = repository.findByOrgKeyAndId(orgKey, documentId)
                .orElseThrow(() -> new DocumentNotFoundException(orgKey, documentId));

        DocumentGraph newGraph = mapper.toGraph(input, document.getGraph());
        List<DocumentValidationProblem> blocking =
                DocumentValidationProblem.blocking(integrityChecker.check(newGraph));
        if (!blocking.isEmpty()) {
            throw new IllegalArgumentException("Invalid document: " + blocking);
        }

        document.replace(input.getTitle(), input.getDescription(), newGraph);
        return repository.save(document);
    }
}
