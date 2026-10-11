package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.DocumentBlock;
import com.processpuzzle.document.usecase.exception.DocumentBlockNotFoundException;
import com.processpuzzle.document.usecase.service.DocumentDraftEditor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class ReplaceDocumentBlock {

    private final DocumentDraftEditor draftEditor;

    public ReplaceDocumentBlock(DocumentDraftEditor draftEditor) {
        this.draftEditor = draftEditor;
    }

    public DocumentBlock execute(String orgKey, String documentId, String locale, String blockId, DocumentBlock input) {
        DocumentBlock replacement = input.withId(blockId);
        draftEditor.apply(orgKey, documentId, locale, current -> {
            List<DocumentBlock> blocks = new ArrayList<>(current);
            blocks.set(indexOf(blocks, blockId, orgKey, documentId), replacement);
            return blocks;
        });
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
