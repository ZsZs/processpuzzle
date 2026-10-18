package com.processpuzzle.document.usecase.service;

import com.processpuzzle.document.domain.ContentBookNode;
import com.processpuzzle.document.domain.ContentBookNodes;
import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentKey;
import com.processpuzzle.document.domain.DocumentKind;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.usecase.exception.ContentBookInvalidTreeException;
import com.processpuzzle.document.usecase.exception.DocumentNotFoundException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns a candidate tree into a {@link ContentBookNodes} the book may hold: structurally valid, and
 * every node showing an existing {@link DocumentKind#DOCUMENT} of the same organization. A report
 * template is refused because it renders once per subject and has nothing to show on its own.
 */
@Component
public class ContentBookTreeChecker {

    private final DocumentRepository documents;

    public ContentBookTreeChecker(DocumentRepository documents) {
        this.documents = documents;
    }

    public ContentBookNodes check(String orgKey, List<ContentBookNode> nodes) {
        ContentBookNodes tree;
        try {
            tree = new ContentBookNodes(nodes);
        } catch (IllegalArgumentException invalid) {
            throw new ContentBookInvalidTreeException(invalid.getMessage());
        }

        Map<String, Document> found = documents
                .findAllById(tree.documentIds().stream().map(id -> new DocumentKey(orgKey, id)).toList())
                .stream()
                .collect(Collectors.toMap(Document::getId, Function.identity()));
        for (String documentId : tree.documentIds()) {
            Document document = found.get(documentId);
            if (document == null) {
                throw new DocumentNotFoundException(orgKey, documentId);
            }
            if (document.getKind() != DocumentKind.DOCUMENT) {
                throw new ContentBookInvalidTreeException(
                        "Document '" + documentId + "' is a " + document.getKind() + ", not a DOCUMENT");
            }
        }
        return tree;
    }
}
