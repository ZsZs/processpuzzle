package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.DocumentBlock;
import com.processpuzzle.document.domain.DocumentDraft;
import com.processpuzzle.document.usecase.service.DocumentDraftEditor;
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

    private final DocumentDraftEditor draftEditor;

    public ReorderDocumentBlocks(DocumentDraftEditor draftEditor) {
        this.draftEditor = draftEditor;
    }

    /**
     * {@code blockIds} must be a permutation of this translation's current block ids — no
     * position field exists to fall back on, so an omitted or added id is rejected outright
     * rather than silently dropping or ignoring blocks.
     */
    public List<DocumentBlock> execute(String orgKey, String documentId, String locale, List<String> blockIds) {
        DocumentDraft draft = draftEditor.apply(orgKey, documentId, locale, current -> {
            Map<String, DocumentBlock> byId = current.stream()
                    .collect(Collectors.toMap(DocumentBlock::id, block -> block));

            Set<String> currentIds = new HashSet<>(byId.keySet());
            Set<String> requestedIds = new HashSet<>(blockIds);
            if (!currentIds.equals(requestedIds) || blockIds.size() != current.size()) {
                throw new IllegalArgumentException(
                        "blockIds must be an exact permutation of this translation's current block ids");
            }

            List<DocumentBlock> reordered = new ArrayList<>(blockIds.size());
            for (String id : blockIds) {
                reordered.add(byId.get(id));
            }
            return reordered;
        });
        return draft.getBlocks();
    }
}
