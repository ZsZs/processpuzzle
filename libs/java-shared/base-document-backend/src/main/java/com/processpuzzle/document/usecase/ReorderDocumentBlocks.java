package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentBlock;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.usecase.exception.DocumentNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class ReorderDocumentBlocks {

    private final DocumentRepository repository;

    public ReorderDocumentBlocks(DocumentRepository repository) {
        this.repository = repository;
    }

    /**
     * {@code blockIds} must be a permutation of the document's current block ids — no
     * position field exists to fall back on, so an omitted or added id is rejected outright
     * rather than silently dropping or ignoring blocks.
     */
    public List<DocumentBlock> execute(String orgKey, String documentId, List<String> blockIds) {
        Document document = repository.findByOrgKeyAndId(orgKey, documentId)
                .orElseThrow(() -> new DocumentNotFoundException(orgKey, documentId));

        List<DocumentBlock> current = document.getGraph().blocks();
        Map<String, DocumentBlock> byId = current.stream()
                .collect(Collectors.toMap(DocumentBlock::id, b -> b));

        Set<String> currentIds = new HashSet<>(byId.keySet());
        Set<String> requestedIds = new HashSet<>(blockIds);
        if (!currentIds.equals(requestedIds) || blockIds.size() != current.size()) {
            throw new IllegalArgumentException(
                    "blockIds must be an exact permutation of this document's current block ids");
        }

        List<DocumentBlock> reordered = new ArrayList<>(blockIds.size());
        for (String id : blockIds) {
            reordered.add(byId.get(id));
        }

        document.replaceBlocks(reordered);
        repository.save(document);
        return reordered;
    }
}
