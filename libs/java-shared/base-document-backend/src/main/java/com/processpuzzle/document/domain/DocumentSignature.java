package com.processpuzzle.document.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One person's acceptance of one published revision of a document, given for one object — a simple
 * electronic signature. Written only by {@code SignDocument} and never edited: a signature that could
 * change after the fact would prove nothing.
 *
 * <h2>Why the signature names an object</h2>
 *
 * <p>One document serves many objects — the employment contract is signed once per employee — so
 * "signed" is not a property of the document. {@code subjectType}/{@code subjectId} say whom the
 * signature is for, and are opaque here: this module knows nothing of base-entity and does not check
 * that the object exists.
 *
 * <h2>Why it records the revision and a hash</h2>
 *
 * <p>Republishing replaces the snapshot wholesale, so the snapshot cannot be what a signature points
 * at. {@code revision} says which publication was read, {@code contentHash} what it contained; together
 * they keep the signature meaningful after the content moves on. {@code title} is copied for the same
 * reason, and the row is deliberately kept when the document is deleted — only erasing the tenant
 * removes it.
 */
@Entity
@Table(name = "document_signatures", indexes = {
        @Index(name = "ix_document_signatures_document", columnList = "org_key, document_id"),
        @Index(name = "ix_document_signatures_subject", columnList = "org_key, subject_type, subject_id")})
public class DocumentSignature {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "org_key", nullable = false, length = 63)
    private String orgKey;

    @Column(name = "document_id", nullable = false, length = 36)
    private String documentId;

    @Column(length = 200)
    private String title;

    @Column(nullable = false, length = 6)
    private String locale;

    @Column(nullable = false)
    private Long revision;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "subject_type", nullable = false, length = 100)
    private String subjectType;

    @Column(name = "subject_id", nullable = false, length = 100)
    private String subjectId;

    @Column(name = "signed_by", nullable = false, length = 200)
    private String signedBy;

    @Column(name = "signed_at", nullable = false)
    private Instant signedAt;

    protected DocumentSignature() {
        // required by JPA
    }

    @SuppressWarnings("java:S107") // one parameter per recorded fact; a builder would only rename them
    public DocumentSignature(String id, String orgKey, String documentId, String title, String locale, Long revision,
                             String contentHash, String subjectType, String subjectId, String signedBy,
                             Instant signedAt) {
        this.id = id;
        this.orgKey = orgKey;
        this.documentId = documentId;
        this.title = title;
        this.locale = locale;
        this.revision = revision;
        this.contentHash = contentHash;
        this.subjectType = subjectType;
        this.subjectId = subjectId;
        this.signedBy = signedBy;
        this.signedAt = signedAt;
    }

    public String getId() {
        return id;
    }

    public String getOrgKey() {
        return orgKey;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getTitle() {
        return title;
    }

    public String getLocale() {
        return locale;
    }

    public Long getRevision() {
        return revision;
    }

    public String getContentHash() {
        return contentHash;
    }

    public String getSubjectType() {
        return subjectType;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public String getSignedBy() {
        return signedBy;
    }

    public Instant getSignedAt() {
        return signedAt;
    }
}
