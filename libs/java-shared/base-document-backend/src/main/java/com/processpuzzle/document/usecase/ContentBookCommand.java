package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.ContentBook;
import com.processpuzzle.document.domain.ContentBookNode;
import com.processpuzzle.document.domain.DocumentRoles;

import java.util.List;

/**
 * Candidate properties of a content book, independent of serialization and server-owned identity.
 *
 * @param nodes the tree, or {@code null} to leave the persisted one as it is — what the generic
 *              properties form sends, since it does not edit the tree
 */
public record ContentBookCommand(String slug, String title, String description, boolean isPublic,
                                 DocumentRoles roles, List<ContentBookNode> nodes) {

    public ContentBookCommand {
        roles = roles == null ? DocumentRoles.unrestricted() : roles;
        nodes = nodes == null ? null : List.copyOf(nodes);
    }

    public void applyTo(ContentBook book) {
        book.replaceProperties(slug, title, description, isPublic, roles);
    }
}
