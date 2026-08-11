package com.processpuzzle.document.usecase;

import com.processpuzzle.document.adapter.inbound.DocumentMapper;
import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.model.DocumentInput;
import com.processpuzzle.document.usecase.exception.DocumentAlreadyExistsException;
import com.processpuzzle.document.usecase.service.DocumentReferentialIntegrityChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class CreateDocument {

    private final DocumentRepository repository;
    private final DocumentReferentialIntegrityChecker integrityChecker;
    private final DocumentMapper mapper;

    public CreateDocument(DocumentRepository repository,
                           DocumentReferentialIntegrityChecker integrityChecker,
                           DocumentMapper mapper) {
        this.repository = repository;
        this.integrityChecker = integrityChecker;
        this.mapper = mapper;
    }

    public Document execute(String orgKey, DocumentInput input) {
        // Same rationale as CreateRule: an explicit existence check rather than relying on
        // save(), since save() merges on an assigned composite id and would silently
        // overwrite instead of conflicting.
        if (repository.existsByOrgKeyAndId(orgKey, input.getId())) {
            throw new DocumentAlreadyExistsException(orgKey, input.getId());
        }

        Document document = mapper.toDomain(orgKey, input);
        List<DocumentValidationProblem> problems = integrityChecker.check(document.getGraph());
        List<DocumentValidationProblem> blocking = DocumentValidationProblem.blocking(problems);
        if (!blocking.isEmpty()) {
            throw new IllegalArgumentException("Invalid document: " + blocking);
        }

        return repository.save(document);
    }
}
