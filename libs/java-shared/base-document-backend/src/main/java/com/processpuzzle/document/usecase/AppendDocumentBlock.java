package com.processpuzzle.document.usecase;

import com.processpuzzle.document.adapter.inbound.DocumentMapper;
import com.processpuzzle.document.domain.DocumentBlock;
import com.processpuzzle.document.model.DocumentBlockInput;
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
    private final DocumentMapper mapper;

    public AppendDocumentBlock(DocumentDraftEditor draftEditor, DocumentMapper mapper) {
        this.draftEditor = draftEditor;
        this.mapper = mapper;
    }

    public DocumentBlock execute(String orgKey, String documentId, String locale, DocumentBlockInput input) {
        DocumentBlock newBlock = mapper.toBlock(UUID.randomUUID().toString(), input);
        draftEditor.apply(orgKey, documentId, locale, current -> {
            List<DocumentBlock> updated = new ArrayList<>(current);
            updated.add(newBlock);
            return updated;
        });
        return newBlock;
    }
}
