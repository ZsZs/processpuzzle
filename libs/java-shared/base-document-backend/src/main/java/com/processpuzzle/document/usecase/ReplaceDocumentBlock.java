package com.processpuzzle.document.usecase;

import com.processpuzzle.document.adapter.inbound.DocumentMapper;
import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentBlock;
import com.processpuzzle.document.domain.DocumentGraph;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.model.DocumentBlockInput;
import com.processpuzzle.document.usecase.exception.DocumentBlockNotFoundException;
import com.processpuzzle.document.usecase.exception.DocumentNotFoundException;
import com.processpuzzle.document.usecase.service.DocumentReferentialIntegrityChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class ReplaceDocumentBlock {

    private final DocumentRepository repository;
    private final DocumentReferentialIntegrityChecker integrityChecker;
    private final DocumentMapper mapper;

    public ReplaceDocumentBlock(DocumentRepository repository,
                                 DocumentReferentialIntegrityChecker integrityChecker,
                                 DocumentMapper mapper) {
        this.repository = repository;
        this.integrityChecker = integrityChecker;
        this.mapper = mapper;
    }

    public DocumentBlock execute(String orgKey, String documentId, String blockId, DocumentBlockInput input) {
        Document document = repository.findByOrgKeyAndId(orgKey, documentId)
                .orElseThrow(() -> new DocumentNotFoundException(orgKey, documentId));

        List<DocumentBlock> blocks = new ArrayList<>(document.getGraph().blocks());
        int index = indexOf(blocks, blockId, orgKey, documentId);

        DocumentBlock replacement = mapper.toBlock(blockId, input);
        blocks.set(index, replacement);
        DocumentGraph candidate = document.getGraph().withBlocks(blocks);

        List<DocumentValidationProblem> blocking =
                DocumentValidationProblem.blocking(integrityChecker.check(candidate));
        if (!blocking.isEmpty()) {
            throw new IllegalArgumentException("Invalid block: " + blocking);
        }

        document.replaceBlocks(blocks);
        repository.save(document);
        return replacement;
    }

    private static int indexOf(List<DocumentBlock> blocks, String blockId, String orgKey, String documentId) {
        for (int i = 0; i < blocks.size(); i++) {
            if (blocks.get(i).id().equals(blockId)) {
                return i;
            }
        }
        throw new DocumentBlockNotFoundException(orgKey, documentId, blockId);
    }
}
