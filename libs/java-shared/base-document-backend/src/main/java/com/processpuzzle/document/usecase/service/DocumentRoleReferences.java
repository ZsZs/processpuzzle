package com.processpuzzle.document.usecase.service;

import com.processpuzzle.core.tenancy.RoleReference;
import com.processpuzzle.core.tenancy.RoleReferenceContributor;
import com.processpuzzle.document.domain.ContentBook;
import com.processpuzzle.document.domain.ContentBookRepository;
import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.domain.DocumentRoles;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The roles documents and content books name as their readers, editors and publishers, so that the
 * organization's role catalog can tell an administrator what still depends on a role before it is
 * deleted — a document whose only reader role is gone is readable by nobody.
 *
 * <p>A content book has no publisher roles of its own to speak of ({@code ContentBook} says so), so
 * only its readers and editors are reported.
 */
@Component
public class DocumentRoleReferences implements RoleReferenceContributor {

    static final String FEATURE_DOCUMENT = "document";
    static final String FEATURE_CONTENT_BOOK = "content-book";
    static final String READER = "reader-role";
    static final String EDITOR = "editor-role";
    static final String PUBLISHER = "publisher-role";

    private final DocumentRepository documents;
    private final ContentBookRepository books;

    public DocumentRoleReferences(DocumentRepository documents, ContentBookRepository books) {
        this.documents = documents;
        this.books = books;
    }

    @Override
    public Map<String, List<RoleReference>> referencedRoles(String orgKey) {
        Map<String, List<RoleReference>> referenced = new LinkedHashMap<>();
        for (Document document : documents.findByOrgKey(orgKey)) {
            DocumentRoles roles = document.getRoles();
            add(referenced, roles.readerRoles(), FEATURE_DOCUMENT, READER, document.getId(), document.getTitle());
            add(referenced, roles.editorRoles(), FEATURE_DOCUMENT, EDITOR, document.getId(), document.getTitle());
            add(referenced, roles.publisherRoles(), FEATURE_DOCUMENT, PUBLISHER, document.getId(), document.getTitle());
        }
        for (ContentBook book : books.findByOrgKey(orgKey)) {
            DocumentRoles roles = book.getRoles();
            add(referenced, roles.readerRoles(), FEATURE_CONTENT_BOOK, READER, book.getId(), book.getTitle());
            add(referenced, roles.editorRoles(), FEATURE_CONTENT_BOOK, EDITOR, book.getId(), book.getTitle());
        }
        return referenced;
    }

    private static void add(Map<String, List<RoleReference>> referenced, List<String> roles,
                            String feature, String kind, String id, String title) {
        for (String role : roles) {
            referenced.computeIfAbsent(role, name -> new ArrayList<>()).add(new RoleReference(feature, kind, id, title));
        }
    }
}
