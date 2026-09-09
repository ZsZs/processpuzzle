package com.processpuzzle.document.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A document's language-invariant half: metadata, port declarations and access control.
 * Identified by ({@code orgKey}, {@code id}) — see {@link DocumentKey}.
 *
 * <h2>Identity and addressing</h2>
 *
 * <p>{@code id} is a server-assigned UUID, so a document is handled like any other
 * ProcessPuzzle entity and every internal reference to it survives a rename. {@code slug} is the
 * separate human-readable route key, unique within the organization, and is what a reader's URL
 * carries. Splitting the two is what lets the public path read {@code /docs/getting-started}
 * while references stay stable when someone renames the page.
 *
 * <h2>What is here and what is per locale</h2>
 *
 * <p>Everything on this entity is invariant across languages, because it is editor-facing:
 * title, subject, description, author, ports, roles, timestamps. Only block content varies by
 * locale, and it lives in {@link DocumentDraft} and {@link PublishedDocument}. One consequence
 * worth knowing: {@code title} is the editor's label, <em>not</em> the heading a reader sees —
 * that comes from the content's own first heading block and is therefore translated.
 *
 * <p>The metadata are real columns rather than parts of one JSON value because
 * {@code listDocuments} filters and sorts on them via RSQL. {@code isPublic} is a real column for
 * the same reason, while the three role lists are one JSON column: they are read whole when
 * authorizing and never filtered on.
 *
 * <h2>Publication</h2>
 *
 * <p>{@code publishedAt} here is the <em>document's</em> publication date — set when any locale
 * is first published and never moved afterwards. Each translation carries its own. This entity
 * holds no revision counter at all: content revisions belong to the translation that has the
 * content, and {@code lockVersion} is a genuine Hibernate-managed {@code @Version} used only for
 * optimistic locking on metadata writes.
 *
 * <p>Publishing a document is an <em>editorial</em> act on content rather than a configuration
 * change, so — unlike {@code AppDefinition} — it is deliberately not a candidate for any
 * organization-wide configuration release train. An earlier revision of this class deferred that
 * question; this is the answer. The second divergence from {@code AppDefinition} is that the
 * published snapshot is its own entity rather than a second column beside the draft, so that "a
 * draft is never publicly readable" holds because of what the public path reads rather than
 * because of a check someone has to remember to perform.
 */
@Entity
@Table(
        name = "documents",
        uniqueConstraints = @UniqueConstraint(name = "uk_documents_org_slug", columnNames = {"org_key", "slug"}))
@IdClass(DocumentKey.class)
public class Document {

    @Id
    @Column(name = "org_key", length = 63)
    private String orgKey;

    @Id
    @Column(name = "document_id", length = 36)
    private String id;

    @Column(nullable = false, length = 100)
    private String slug;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 200)
    private String subject;

    @Column(length = 1000)
    private String description;

    @Column(length = 200)
    private String author;

    @Column(name = "source_locale", nullable = false, length = 6)
    private String sourceLocale;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Convert(converter = DocumentRolesConverter.class)
    private DocumentRoles roles = DocumentRoles.unrestricted();

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Convert(converter = DocumentPortsConverter.class)
    private DocumentPorts ports = DocumentPorts.empty();

    @Version
    @Column(name = "lock_version")
    private Long lockVersion;

    @Column(name = "created_by", length = 200, updatable = false)
    private String createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Document() {
        // required by JPA
    }

    public Document(String orgKey, String id, String slug, String title, String sourceLocale, String createdBy) {
        this.orgKey = orgKey;
        this.id = id;
        this.slug = slug;
        this.title = title;
        this.sourceLocale = sourceLocale;
        this.createdBy = createdBy;
        this.author = createdBy;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * Replaces every editable invariant field — what {@code UpdateDocumentProperties} and the
     * metadata half of {@code UpdateDocument} both apply. Content is untouched by construction:
     * it is not on this entity at all.
     */
    public void replaceProperties(String newSlug, String newTitle, String newSubject, String newDescription,
                                  String newAuthor, String newSourceLocale, boolean newIsPublic,
                                  DocumentRoles newRoles, DocumentPorts newPorts) {
        this.slug = newSlug;
        this.title = newTitle;
        this.subject = newSubject;
        this.description = newDescription;
        this.author = newAuthor;
        this.sourceLocale = newSourceLocale;
        this.isPublic = newIsPublic;
        this.roles = newRoles == null ? DocumentRoles.unrestricted() : newRoles;
        this.ports = newPorts == null ? DocumentPorts.empty() : newPorts;
    }

    /**
     * Records the first publication of any locale. Later publishes are no-ops here, so
     * {@code publishedAt} keeps meaning "when this document went live" rather than "when it was
     * last touched" — {@code updatedAt} and each translation's own {@code publishedAt} answer
     * that question.
     */
    public void markFirstPublication(Instant when) {
        if (this.publishedAt == null) {
            this.publishedAt = when;
        }
    }

    public String getOrgKey() {
        return orgKey;
    }

    public String getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getTitle() {
        return title;
    }

    public String getSubject() {
        return subject;
    }

    public String getDescription() {
        return description;
    }

    public String getAuthor() {
        return author;
    }

    public String getSourceLocale() {
        return sourceLocale;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public DocumentRoles getRoles() {
        return roles == null ? DocumentRoles.unrestricted() : roles;
    }

    public DocumentPorts getPorts() {
        return ports == null ? DocumentPorts.empty() : ports;
    }

    public Long getLockVersion() {
        return lockVersion;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
