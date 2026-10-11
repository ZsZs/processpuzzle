package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.DocumentBlock;
import com.processpuzzle.document.usecase.service.DocumentDraftEditor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class AppendDocumentBlock {

    private final DocumentDraftEditor draftEditor;

    public AppendDocumentBlock(DocumentDraftEditor draftEditor) {
        this.draftEditor = draftEditor;
    }

    public DocumentBlock execute(String orgKey, String documentId, String locale, DocumentBlock input) {
        DocumentBlock newBlock = input.withId(UUID.randomUUID().toString());
        draftEditor.apply(orgKey, documentId, locale, current -> {
            List<DocumentBlock> updated = new ArrayList<>(current);
            updated.add(newBlock);
            return updated;
        });
        return newBlock;
    }
}
