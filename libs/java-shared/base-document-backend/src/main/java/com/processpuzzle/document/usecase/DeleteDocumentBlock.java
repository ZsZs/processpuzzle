package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.DocumentBlock;
import com.processpuzzle.document.domain.DocumentContent;
import com.processpuzzle.document.usecase.exception.DocumentBlockNotFoundException;
import com.processpuzzle.document.usecase.exception.DocumentBlockReferencedException;
import com.processpuzzle.document.usecase.service.DocumentDraftEditor;
import com.processpuzzle.document.usecase.service.DocumentReferentialIntegrityChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class DeleteDocumentBlock {

    private final DocumentDraftEditor draftEditor;
    private final DocumentReferentialIntegrityChecker integrityChecker;

    public DeleteDocumentBlock(DocumentDraftEditor draftEditor, DocumentReferentialIntegrityChecker integrityChecker) {
        this.draftEditor = draftEditor;
        this.integrityChecker = integrityChecker;
    }

    public void execute(String orgKey, String documentId, String locale, String blockId) {
        draftEditor.apply(orgKey, documentId, locale, current -> {
            if (current.stream().noneMatch(block -> block.id().equals(blockId))) {
                throw new DocumentBlockNotFoundException(orgKey, documentId, blockId);
            }

            // The referenced-by check runs against the content as it stands, before removal —
            // taking the block out first would make it invisible to its own referrers' childIds
            // and widgetEmbed scan, and the delete would silently leave dangling references.
            List<String> referencingBlockIds = integrityChecker.referencesTo(DocumentContent.of(current), blockId);
            if (!referencingBlockIds.isEmpty()) {
                throw new DocumentBlockReferencedException(blockId, referencingBlockIds);
            }

            List<DocumentBlock> remaining = new ArrayList<>(current);
            remaining.removeIf(block -> block.id().equals(blockId));
            return remaining;
        });
    }
}
