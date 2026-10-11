package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentDraft;

import java.util.List;

/** Authorized editorial state; the calling adapter chooses its representation. */
public record DocumentExport(Document document, List<DocumentDraft> drafts) {
    public DocumentExport {
        drafts = List.copyOf(drafts);
    }
}
