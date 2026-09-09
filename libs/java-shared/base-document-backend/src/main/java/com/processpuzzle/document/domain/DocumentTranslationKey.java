package com.processpuzzle.document.domain;

import java.io.Serializable;
import java.util.Objects;

/**
 * The {@code @IdClass} shared by {@link DocumentDraft} and {@link PublishedDocument}: one
 * translation is identified by its organization, its document and its locale. Shared rather than
 * duplicated per entity because the two tables address the same thing at the same granularity —
 * they differ in what they hold, not in how they are found.
 *
 * <p>JPA requires a public no-arg constructor, {@code equals}/{@code hashCode} over exactly the
 * {@code @Id} fields, and {@link Serializable}.
 */
public class DocumentTranslationKey implements Serializable {

    private static final long serialVersionUID = 1L;

    private String orgKey;
    private String documentId;
    private String locale;

    public DocumentTranslationKey() {
        // required by JPA
    }

    public DocumentTranslationKey(String orgKey, String documentId, String locale) {
        this.orgKey = orgKey;
        this.documentId = documentId;
        this.locale = locale;
    }

    public String getOrgKey() {
        return orgKey;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getLocale() {
        return locale;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DocumentTranslationKey that)) return false;
        return Objects.equals(orgKey, that.orgKey)
                && Objects.equals(documentId, that.documentId)
                && Objects.equals(locale, that.locale);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orgKey, documentId, locale);
    }
}
