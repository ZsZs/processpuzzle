package com.processpuzzle.app.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The root of an application's metadata. Identified by ({@code orgKey}, {@code id}) — see
 * {@link AppDefinitionKey}.
 *
 * <h2>Draft and published revisions</h2>
 *
 * <p>Two graph snapshots are held side by side. {@code draftGraph} is what the designer edits;
 * {@code publishedGraph} is a copy taken at publish time and is what end users are served. That
 * is what lets {@code GET .../layout?draft=false} keep serving the previous revision while
 * someone is mid-edit. There is no revision-history table because the contract exposes no
 * history operation — no list, get-by-revision, diff or rollback — and adding one later is
 * straightforward precisely because the graph is one opaque column.
 *
 * <h2>Why {@code revision} is not {@code @Version}</h2>
 *
 * <p>The contract defines {@code status} as {@code PUBLISHED} exactly when
 * {@code publishedVersion == version}, and publishing must therefore leave the counter alone.
 * Hibernate increments a {@code @Version} field on any dirty flush, and publishing dirties the
 * row — so a managed version would land at {@code publishedRevision + 1} the instant a publish
 * committed, reporting unpublished edits on every freshly published app. {@code revision} is
 * therefore a plain column, bumped explicitly by {@code UpdateAppDefinition}.
 *
 * <p>No optimistic-locking column is exposed either: {@code AppDefinitionInput} carries no
 * version and {@code updateAppDefinition} has no {@code If-Match} header, so a client has no way
 * to send one back. Concurrent designers are last-write-wins.
 *
 * <h2>Header fields are not versioned</h2>
 *
 * <p>{@code name}, {@code translocoId} and {@code description} are real columns rather than part
 * of the graph, because {@code listAppDefinitions} filters and sorts on them via RSQL. They are
 * consequently <em>not</em> snapshotted on publish: renaming an app is visible to end users
 * immediately. Only theme, layout, regions and routes are versioned.
 */
@Entity
@Table(name = "app_definitions")
@IdClass(AppDefinitionKey.class)
public class AppDefinition {

    @Id
    @Column(name = "org_key", length = 63)
    private String orgKey;

    @Id
    @Column(name = "app_id", length = 100)
    private String id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "transloco_id", length = 200)
    private String translocoId;

    @Column(length = 1000)
    private String description;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Convert(converter = AppGraphConverter.class)
    @Column(name = "draft_graph")
    private AppGraph draftGraph = AppGraph.empty();

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Convert(converter = AppGraphConverter.class)
    @Column(name = "published_graph")
    private AppGraph publishedGraph;

    @Column(nullable = false)
    private Long revision = 1L;

    @Column(name = "published_revision")
    private Long publishedRevision;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected AppDefinition() {
        // required by JPA
    }

    public AppDefinition(String orgKey, String id, String name, String translocoId,
                         String description, AppGraph draftGraph) {
        this.orgKey = orgKey;
        this.id = id;
        this.name = name;
        this.translocoId = translocoId;
        this.description = description;
        this.draftGraph = draftGraph == null ? AppGraph.empty() : draftGraph;
        this.revision = 1L;
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
     * Replaces the draft with a new revision, bumping {@link #getRevision()} and leaving the
     * published snapshot untouched — so end users keep seeing the last published revision.
     */
    public void replaceDraft(String newName, String newTranslocoId, String newDescription, AppGraph newGraph) {
        this.name = newName;
        this.translocoId = newTranslocoId;
        this.description = newDescription;
        this.draftGraph = newGraph == null ? AppGraph.empty() : newGraph;
        this.revision = this.revision == null ? 1L : this.revision + 1L;
    }

    /** Promotes the current draft to the published revision. Deliberately does not touch {@code revision}. */
    public void publish() {
        this.publishedGraph = this.draftGraph;
        this.publishedRevision = this.revision;
    }

    /** True when nothing has been edited since the last publish. Source of the contract's {@code status}. */
    public boolean isPublished() {
        return publishedRevision != null && publishedRevision.equals(revision);
    }

    /** True once this definition has been published at least once, even if edited since. */
    public boolean hasPublishedRevision() {
        return publishedGraph != null;
    }

    /**
     * The graph to serve: the draft when {@code draft} is true, otherwise the published snapshot
     * ({@code null} when this definition has never been published).
     */
    public AppGraph graphFor(boolean draft) {
        return draft ? draftGraph : publishedGraph;
    }

    public String getOrgKey() {
        return orgKey;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTranslocoId() {
        return translocoId;
    }

    public String getDescription() {
        return description;
    }

    public AppGraph getDraftGraph() {
        return draftGraph;
    }

    public AppGraph getPublishedGraph() {
        return publishedGraph;
    }

    public Long getRevision() {
        return revision;
    }

    public Long getPublishedRevision() {
        return publishedRevision;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
