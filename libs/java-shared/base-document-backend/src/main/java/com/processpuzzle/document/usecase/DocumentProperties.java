package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentKind;
import com.processpuzzle.document.domain.DocumentPorts;
import com.processpuzzle.document.domain.DocumentRoles;

/** Complete replacement of language-invariant metadata; never contains translation content. */
public record DocumentProperties(
        String slug, String title, String subject, String description, String author,
        String sourceLocale, boolean isPublic, DocumentRoles roles, DocumentPorts ports, DocumentKind kind) {

    public DocumentProperties {
        roles = roles == null ? DocumentRoles.unrestricted() : roles;
        ports = ports == null ? DocumentPorts.empty() : ports;
        kind = kind == null ? DocumentKind.DOCUMENT : kind;
    }

    /** A plain {@link DocumentKind#DOCUMENT} — what every document was before report templates. */
    public DocumentProperties(
            String slug, String title, String subject, String description, String author,
            String sourceLocale, boolean isPublic, DocumentRoles roles, DocumentPorts ports) {
        this(slug, title, subject, description, author, sourceLocale, isPublic, roles, ports, DocumentKind.DOCUMENT);
    }

    public Document create(String orgKey, String id, String principal) {
        Document document = new Document(orgKey, id, slug, title, sourceLocale, principal);
        document.replaceProperties(slug, title, subject, description, author == null ? principal : author,
                sourceLocale, isPublic, roles, ports);
        document.changeKind(kind);
        return document;
    }

    public void applyTo(Document document) {
        document.replaceProperties(slug, title, subject, description, author, sourceLocale, isPublic, roles, ports);
        document.changeKind(kind);
    }
}
