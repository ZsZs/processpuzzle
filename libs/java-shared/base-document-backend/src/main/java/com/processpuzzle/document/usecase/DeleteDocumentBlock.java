package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentBlock;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.usecase.exception.DocumentBlockNotFoundException;
import com.processpuzzle.document.usecase.exception.DocumentBlockReferencedException;
import com.processpuzzle.document.usecase.exception.DocumentNotFoundException;
import com.processpuzzle.document.usecase.service.DocumentReferentialIntegrityChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class DeleteDocumentBlock {

    private final DocumentRepository repository;
    private final DocumentReferentialIntegrityChecker integrityChecker;

    public DeleteDocumentBlock(DocumentRepository repository,
                                DocumentReferentialIntegrityChecker integrityChecker) {
        this.repository = repository;
        this.integrityChecker = integrityChecker;
    }

    public void execute(String orgKey, String documentId, String blockId) {
        Document document = repository.findByOrgKeyAndId(orgKey, documentId)
                .orElseThrow(() -> new DocumentNotFoundException(orgKey, documentId));

        List<DocumentBlock> blocks = document.getGraph().blocks();
        boolean exists = blocks.stream().anyMatch(b -> b.id().equals(blockId));
        if (!exists) {
            throw new DocumentBlockNotFoundException(orgKey, documentId, blockId);
        }

        // Referenced-by check runs against the *current* graph, before removal — deleting the
        // block first would make it invisible to its own referrers' childIds/widgetEmbed scan.
        List<String> referencingBlockIds = integrityChecker.referencesTo(document.getGraph(), blockId);
        if (!referencingBlockIds.isEmpty()) {
            throw new DocumentBlockReferencedException(blockId, referencingBlockIds);
        }

        List<DocumentBlock> remaining = new ArrayList<>(blocks);
        remaining.removeIf(b -> b.id().equals(blockId));
        document.replaceBlocks(remaining);
        repository.save(document);
    }
}
