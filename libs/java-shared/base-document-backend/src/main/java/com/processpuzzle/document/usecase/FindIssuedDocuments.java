package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.domain.DocumentSignature;
import com.processpuzzle.document.domain.DocumentSignatureRepository;
import com.processpuzzle.document.usecase.service.DocumentGuard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The organization's issued documents: its signatures, newest first, each of which may carry the PDF of
 * the report that was signed. Narrowed optionally to one document or one object.
 *
 * <p>Needs organization access, like every list; a signature whose document the principal may not read
 * is left out (a deleted document's signature too), which can shorten a page — the trade {@link FindAllDocuments} makes too.
 */
@Service
@Transactional(readOnly = true)
public class FindIssuedDocuments {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 200;

    private final DocumentSignatureRepository signatureRepository;
    private final DocumentRepository documents;
    private final DocumentGuard guard;

    public FindIssuedDocuments(DocumentSignatureRepository signatureRepository, DocumentRepository documents,
                               DocumentGuard guard) {
        this.signatureRepository = signatureRepository;
        this.documents = documents;
        this.guard = guard;
    }

    public Page<DocumentSignature> execute(String orgKey, String documentId, String subjectType, String subjectId,
                                           Integer page, Integer size) {
        guard.requireOrganizationAccess(orgKey);
        int pageSize = size == null ? DEFAULT_SIZE : Math.max(1, Math.min(size, MAX_SIZE));
        Page<DocumentSignature> found = signatureRepository.findIssued(orgKey, documentId, subjectType, subjectId,
                PageRequest.of(page == null ? 0 : Math.max(0, page), pageSize));
        List<DocumentSignature> readable = found.getContent().stream()
                .filter(signature -> documents.findByOrgKeyAndId(orgKey, signature.getDocumentId())
                        .map(guard::canRead).orElse(false))
                .toList();
        return new PageImpl<>(readable, found.getPageable(), found.getTotalElements());
    }
}
