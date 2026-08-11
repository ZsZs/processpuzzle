package com.processpuzzle.document.usecase;

import com.processpuzzle.document.adapter.inbound.DocumentMapper;
import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentBlock;
import com.processpuzzle.document.domain.DocumentGraph;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.model.DocumentBlockInput;
import com.processpuzzle.document.usecase.exception.DocumentNotFoundException;
import com.processpuzzle.document.usecase.service.DocumentReferentialIntegrityChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class AppendDocumentBlock {

    private final DocumentRepository repository;
    private final DocumentReferentialIntegrityChecker integrityChecker;
    private final DocumentMapper mapper;

    public AppendDocumentBlock(DocumentRepository repository,
                                DocumentReferentialIntegrityChecker integrityChecker,
                                DocumentMapper mapper) {
        this.repository = repository;
        this.integrityChecker = integrityChecker;
        this.mapper = mapper;
    }

    public DocumentBlock execute(String orgKey, String documentId, DocumentBlockInput input) {
        Document document = repository.findByOrgKeyAndId(orgKey, documentId)
                .orElseThrow(() -> new DocumentNotFoundException(orgKey, documentId));

        DocumentBlock newBlock = mapper.toBlock(UUID.randomUUID().toString(), input);
        List<DocumentBlock> updated = new ArrayList<>(document.getGraph().blocks());
        updated.add(newBlock);
        DocumentGraph candidate = document.getGraph().withBlocks(updated);

        List<DocumentValidationProblem> blocking =
                DocumentValidationProblem.blocking(integrityChecker.check(candidate));
        if (!blocking.isEmpty()) {
            throw new IllegalArgumentException("Invalid block: " + blocking);
        }

        document.replaceBlocks(updated);
        repository.save(document);
        return newBlock;
    }
}
