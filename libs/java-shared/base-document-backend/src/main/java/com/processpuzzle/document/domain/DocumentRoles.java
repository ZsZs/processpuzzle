package com.processpuzzle.document.domain;

import java.util.List;

/**
 * Who may read, edit and publish a document, persisted as one JSON column on {@link Document}.
 *
 * <p>Plain role names with no referential link to a role registry — the same convention
 * {@code NavNode.roles} establishes in base-app. That is what lets these lists later be offered
 * from base-workflow definitions as a picker data source rather than requiring a schema change,
 * and it is what keeps the lists evaluable by a store that authorizes with rules over the record
 * itself and has no server in the path.
 *
 * <p>An empty list means "any authenticated member of the organization", again as
 * {@code NavNode.roles} defines it — not "nobody". Anonymous access is a separate, explicit
 * decision held in {@code Document.isPublic}, deliberately not expressible as an empty role list,
 * so widening a document to the whole internet cannot happen by clearing a field.
 */
public record DocumentRoles(
        List<String> readerRoles,
        List<String> editorRoles,
        List<String> publisherRoles) {

    public DocumentRoles {
        readerRoles = readerRoles == null ? List.of() : List.copyOf(readerRoles);
        editorRoles = editorRoles == null ? List.of() : List.copyOf(editorRoles);
        publisherRoles = publisherRoles == null ? List.of() : List.copyOf(publisherRoles);
    }

    public static DocumentRoles unrestricted() {
        return new DocumentRoles(List.of(), List.of(), List.of());
    }

    /**
     * Publishing is a distinct authority from editing, but a deployment that does not care to
     * separate them should not have to declare it twice — so an empty publisher list falls back to
     * the editor list rather than to "any member".
     */
    public List<String> effectivePublisherRoles() {
        return publisherRoles.isEmpty() ? editorRoles : publisherRoles;
    }
}
