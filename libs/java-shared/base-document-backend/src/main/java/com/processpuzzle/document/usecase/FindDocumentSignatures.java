package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.domain.DocumentSignature;
import com.processpuzzle.document.domain.DocumentSignatureRepository;
import com.processpuzzle.document.usecase.exception.DocumentNotFoundException;
import com.processpuzzle.document.usecase.service.DocumentGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * A document's signatures, newest first, optionally narrowed to the object they were given for. Readable
 * by whoever may read the document, and answered 404 otherwise, for the reason {@link FindPublishedContent}
 * gives.
 */
@Service
@Transactional(readOnly = true)
public class FindDocumentSignatures {

    private final DocumentRepository repository;
    private final DocumentSignatureRepository signatureRepository;
    private final DocumentGuard guard;

    public FindDocumentSignatures(DocumentRepository repository,
                                  DocumentSignatureRepository signatureRepository,
                                  DocumentGuard guard) {
        this.repository = repository;
        this.signatureRepository = signatureRepository;
        this.guard = guard;
    }

    /** @param subjectType and {@code subjectId} each narrow the list when not null */
    public List<DocumentSignature> execute(String orgKey, String documentId, String subjectType, String subjectId) {
        Document document = repository.findByOrgKeyAndId(orgKey, documentId)
                .orElseThrow(() -> new DocumentNotFoundException(orgKey, documentId));
        if (!guard.canRead(document)) {
            throw new DocumentNotFoundException(orgKey, documentId);
        }
        return signatureRepository.findByOrgKeyAndDocumentIdOrderBySignedAtDesc(orgKey, documentId).stream()
                .filter(signature -> subjectType == null || subjectType.equals(signature.getSubjectType()))
                .filter(signature -> subjectId == null || subjectId.equals(signature.getSubjectId()))
                .toList();
    }
}
