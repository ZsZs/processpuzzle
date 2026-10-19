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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * A navigable body of content — a tree of existing {@link Document}s with its own access rights,
 * the way a Confluence space groups pages. Identified by ({@code orgKey}, {@code id}), addressed by
 * {@code slug}, exactly as a document is.
 *
 * <h2>The book owns the tree</h2>
 *
 * <p>The tree is {@link ContentBookNodes} on the book, not a parent link on each document. Documents
 * are therefore untouched by being placed in a book, one document can be filed in several books, and
 * a reorder is one write of one row.
 *
 * <h2>Access</h2>
 *
 * <p>{@code isPublic} and {@link DocumentRoles} follow the document convention and are evaluated by the
 * same {@code DocumentGuard} ladder. {@code publisherRoles} is unused: a book has nothing to publish.
 * The book's roles guard the book — its listing, its tree, its properties. They do not widen access
 * to a document: a reader of the book who may not read a document in it still does not see that
 * document's content.
 */
@Entity
@Table(
        name = "content_books",
        uniqueConstraints = @UniqueConstraint(name = "uk_content_books_org_slug", columnNames = {"org_key", "slug"}))
@IdClass(ContentBookKey.class)
public class ContentBook {

    @Id
    @Column(name = "org_key", length = 63)
    private String orgKey;

    @Id
    @Column(name = "content_book_id", length = 36)
    private String id;

    @Column(nullable = false, length = 100)
    private String slug;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Convert(converter = DocumentRolesConverter.class)
    private DocumentRoles roles = DocumentRoles.unrestricted();

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Convert(converter = ContentBookNodesConverter.class)
    private ContentBookNodes nodes = ContentBookNodes.empty();

    @Version
    @Column(name = "lock_version")
    private Long lockVersion;

    @Column(name = "created_by", length = 200, updatable = false)
    private String createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ContentBook() {
        // required by JPA
    }

    public ContentBook(String orgKey, String id, String slug, String title, String createdBy) {
        this.orgKey = orgKey;
        this.id = id;
        this.slug = slug;
        this.title = title;
        this.createdBy = createdBy;
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

    public void replaceProperties(String newSlug, String newTitle, String newDescription, boolean newIsPublic,
                                  DocumentRoles newRoles) {
        this.slug = newSlug;
        this.title = newTitle;
        this.description = newDescription;
        this.isPublic = newIsPublic;
        this.roles = newRoles == null ? DocumentRoles.unrestricted() : newRoles;
    }

    public void replaceNodes(ContentBookNodes newNodes) {
        this.nodes = newNodes == null ? ContentBookNodes.empty() : newNodes;
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

    public String getDescription() {
        return description;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public DocumentRoles getRoles() {
        return roles == null ? DocumentRoles.unrestricted() : roles;
    }

    public ContentBookNodes getNodes() {
        return nodes == null ? ContentBookNodes.empty() : nodes;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
