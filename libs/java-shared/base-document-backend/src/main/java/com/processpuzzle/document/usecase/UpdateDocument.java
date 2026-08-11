package com.processpuzzle.document.usecase;

import com.processpuzzle.document.adapter.inbound.DocumentMapper;
import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentGraph;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.model.DocumentInput;
import com.processpuzzle.document.usecase.exception.DocumentNotFoundException;
import com.processpuzzle.document.usecase.service.DocumentReferentialIntegrityChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class UpdateDocument {

    private final DocumentRepository repository;
    private final DocumentReferentialIntegrityChecker integrityChecker;
    private final DocumentMapper mapper;

    public UpdateDocument(DocumentRepository repository,
                           DocumentReferentialIntegrityChecker integrityChecker,
                           DocumentMapper mapper) {
        this.repository = repository;
        this.integrityChecker = integrityChecker;
        this.mapper = mapper;
    }

    public Document execute(String orgKey, String documentId, DocumentInput input) {
        Document document = repository.findByOrgKeyAndId(orgKey, documentId)
                .orElseThrow(() -> new DocumentNotFoundException(orgKey, documentId));

        DocumentGraph newGraph = mapper.toGraph(input);
        List<DocumentValidationProblem> blocking =
                DocumentValidationProblem.blocking(integrityChecker.check(newGraph));
        if (!blocking.isEmpty()) {
            throw new IllegalArgumentException("Invalid document: " + blocking);
        }

        // No explicit version bump — @Version on Document.version handles optimistic locking
        // on flush, unlike AppDefinition.revision which is a plain column for draft/publish
        // reasons that don't apply here yet. Spring Data JPA raises
        // ObjectOptimisticLockingFailureException on a stale write; the exception handler
        // maps that to 409, same status the contract's Conflict response promises.
        document.replace(input.getTitle(), input.getDescription(), newGraph);
        return repository.save(document);
    }
}
