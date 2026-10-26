package com.processpuzzle.document.usecase.service;

import com.processpuzzle.core.tenancy.RoleReference;
import com.processpuzzle.document.domain.ContentBook;
import com.processpuzzle.document.domain.ContentBookRepository;
import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.domain.DocumentRoles;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentRoleReferencesTest {

    private static final String ORG = "acme";

    private final DocumentRepository documents = mock(DocumentRepository.class);
    private final ContentBookRepository books = mock(ContentBookRepository.class);
    private final DocumentRoleReferences references = new DocumentRoleReferences(documents, books);

    @Test
    void aDocumentReportsEveryRoleItNamesAndInWhichCapacity() {
        Document handbook = document("doc-1", "Handbook",
                new DocumentRoles(List.of("staff"), List.of("editor", "staff"), List.of("editor")));
        when(documents.findByOrgKey(ORG)).thenReturn(List.of(handbook));
        when(books.findByOrgKey(ORG)).thenReturn(List.of());

        Map<String, List<RoleReference>> referenced = references.referencedRoles(ORG);

        assertThat(referenced).containsOnlyKeys("staff", "editor");
        assertThat(referenced.get("staff")).containsExactly(
                new RoleReference("document", "reader-role", "doc-1", "Handbook"),
                new RoleReference("document", "editor-role", "doc-1", "Handbook"));
        assertThat(referenced.get("editor")).containsExactly(
                new RoleReference("document", "editor-role", "doc-1", "Handbook"),
                new RoleReference("document", "publisher-role", "doc-1", "Handbook"));
    }

    /** A book has nothing to publish, so its publisher list is not a reference. */
    @Test
    void aContentBookReportsItsReadersAndEditorsOnly() {
        when(documents.findByOrgKey(ORG)).thenReturn(List.of());
        ContentBook book = mock(ContentBook.class);
        when(book.getId()).thenReturn("book-1");
        when(book.getTitle()).thenReturn("Manual");
        when(book.getRoles()).thenReturn(new DocumentRoles(List.of("staff"), List.of("author"), List.of("ignored")));
        when(books.findByOrgKey(ORG)).thenReturn(List.of(book));

        Map<String, List<RoleReference>> referenced = references.referencedRoles(ORG);

        assertThat(referenced).containsOnlyKeys("staff", "author");
        assertThat(referenced.get("author")).containsExactly(
                new RoleReference("content-book", "editor-role", "book-1", "Manual"));
    }

    @Test
    void unrestrictedDocumentsNameNoRole() {
        Document open = document("doc-1", "Open", DocumentRoles.unrestricted());
        when(documents.findByOrgKey(ORG)).thenReturn(List.of(open));
        when(books.findByOrgKey(ORG)).thenReturn(List.of());

        assertThat(references.referencedRoles(ORG)).isEmpty();
    }

    private static Document document(String id, String title, DocumentRoles roles) {
        Document document = mock(Document.class);
        when(document.getId()).thenReturn(id);
        when(document.getTitle()).thenReturn(title);
        when(document.getRoles()).thenReturn(roles);
        return document;
    }
}
