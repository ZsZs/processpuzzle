package com.processpuzzle.document.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.List;

/**
 * The snapshot of one locale's content that readers are served. Written only by
 * {@code PublishDocumentTranslation}, deleted only by unpublishing or by deleting the document.
 *
 * <h2>Why this is its own entity</h2>
 *
 * <p>{@code AppDefinition} keeps its draft and published graphs side by side in one row, and that
 * is fine there because an app definition is never read anonymously. Document content is: the
 * public read path serves published content to unauthenticated callers when the document is
 * public. Holding the draft in the same record as the snapshot would mean every read that is
 * allowed to see the snapshot is physically reading the draft too, and the only thing keeping the
 * draft out of the response would be the code remembering to drop the field.
 *
 * <p>Making it a separate entity turns that into a property of the model: the public path queries
 * this table and has no route to a draft at all. It is also what makes the design portable to a
 * store that authorizes with rules over whole records — Firestore rules can allow or deny a
 * document but cannot hide one of its fields, so a shared record would be readable in full or not
 * at all.
 *
 * <p>There is no {@code @Version} here. A snapshot is replaced wholesale by a publish rather than
 * edited, so there is no concurrent-edit window to protect: the draft it was copied from carries
 * the lock.
 */
@Entity
@Table(name = "published_documents")
@IdClass(DocumentTranslationKey.class)
public class PublishedDocument {

    @Id
    @Column(name = "org_key", length = 63)
    private String orgKey;

    @Id
    @Column(name = "document_id", length = 36)
    private String documentId;

    @Id
    @Column(length = 6)
    private String locale;

    @Lob
    @Convert(converter = DocumentContentConverter.class)
    private DocumentContent content = DocumentContent.empty();

    /**
     * The draft {@code revision} this snapshot was taken from. Compared against the draft's
     * current revision to tell a current publication from one with unpublished edits behind it.
     */
    @Column(name = "published_revision", nullable = false)
    private Long publishedRevision;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @Column(name = "published_by", length = 200)
    private String publishedBy;

    protected PublishedDocument() {
        // required by JPA
    }

    public PublishedDocument(String orgKey, String documentId, String locale, DocumentContent content,
                             Long publishedRevision, Instant publishedAt, String publishedBy) {
        this.orgKey = orgKey;
        this.documentId = documentId;
        this.locale = locale;
        this.content = content == null ? DocumentContent.empty() : content;
        this.publishedRevision = publishedRevision;
        this.publishedAt = publishedAt;
        this.publishedBy = publishedBy;
    }

    /** Republishes: the snapshot is replaced wholesale rather than merged. */
    public void replaceSnapshot(DocumentContent newContent, Long newPublishedRevision, Instant when, String by) {
        this.content = newContent == null ? DocumentContent.empty() : newContent;
        this.publishedRevision = newPublishedRevision;
        this.publishedAt = when;
        this.publishedBy = by;
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

    public DocumentContent getContent() {
        return content == null ? DocumentContent.empty() : content;
    }

    public List<DocumentBlock> getBlocks() {
        return getContent().blocks();
    }

    public Long getPublishedRevision() {
        return publishedRevision;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public String getPublishedBy() {
        return publishedBy;
    }
}
