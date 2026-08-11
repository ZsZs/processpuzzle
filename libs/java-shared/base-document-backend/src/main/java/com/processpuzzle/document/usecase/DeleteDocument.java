package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.usecase.exception.DocumentNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DeleteDocument {

    private final DocumentRepository repository;

    public DeleteDocument(DocumentRepository repository) {
        this.repository = repository;
    }

    public void execute(String orgKey, String documentId) {
        if (!repository.existsByOrgKeyAndId(orgKey, documentId)) {
            throw new DocumentNotFoundException(orgKey, documentId);
        }
        repository.deleteById(new com.processpuzzle.document.domain.DocumentKey(orgKey, documentId));
    }
}
