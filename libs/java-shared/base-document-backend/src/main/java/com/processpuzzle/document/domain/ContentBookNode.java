package com.processpuzzle.document.domain;

/**
 * One place of a document in a {@link ContentBook}'s tree.
 *
 * @param id         unique within the book, client-assigned; what children name as {@code parentId}
 * @param documentId the {@link Document} shown at this place
 * @param parentId   the parent node's {@code id}, {@code null} for a top-level node
 * @param orderIndex position among siblings
 */
public record ContentBookNode(String id, String documentId, String parentId, int orderIndex) {

    public ContentBookNode withPlace(String newParentId, int newOrderIndex) {
        return new ContentBookNode(id, documentId, newParentId, newOrderIndex);
    }
}
