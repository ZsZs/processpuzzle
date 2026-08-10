package com.processpuzzle.artifact.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * A persisted artifact. Identified by ({@code orgKey}, {@code id}) — see {@link ArtifactKey}.
 *
 * <p>{@code title} and {@code description} are real columns rather than part of the graph,
 * same rationale {@code AppDefinition} documents: {@code listArtifacts} filters and sorts on
 * {@code title} via RSQL. Everything else — ports and the block list — lives in one opaque
 * {@link ArtifactGraph} column.
 *
 * <p>Unlike {@code AppDefinition}, there is no draft/published split here yet: {@code version}
 * is a genuine Hibernate-managed {@code @Version}, bumped automatically on every write and
 * usable as-is for optimistic locking. <b>This will need to change</b> if the proposed
 * organization-wide {@code ConfigurationRelease} train lands — at that point publishing stops
 * being a per-resource concern and {@code version} would need to become a plain counter the
 * same way {@code AppDefinition.revision} is, so a release can pin a specific past version
 * without Hibernate's managed counter fighting it. Deliberately not solved here; revisit
 * together once that design is confirmed.
 */
@Entity
@Table(name = "artifacts")
@IdClass(ArtifactKey.class)
public class Artifact {

    @Id
    @Column(name = "org_key", length = 63)
    private String orgKey;

    @Id
    @Column(name = "artifact_id", length = 100)
    private String id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 1000)
    private String description;

    @Lob
    @Convert(converter = ArtifactGraphConverter.class)
    private ArtifactGraph graph = ArtifactGraph.empty();

    @Version
    private Long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Artifact() {
        // required by JPA
    }

    public Artifact(String orgKey, String id, String title, String description, ArtifactGraph graph) {
        this.orgKey = orgKey;
        this.id = id;
        this.title = title;
        this.description = description;
        this.graph = graph == null ? ArtifactGraph.empty() : graph;
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

    /** Whole-document replace, used by {@code UpdateArtifact}. */
    public void replace(String newTitle, String newDescription, ArtifactGraph newGraph) {
        this.title = newTitle;
        this.description = newDescription;
        this.graph = newGraph == null ? ArtifactGraph.empty() : newGraph;
    }

    /** Replaces only the block list, used by the block-level use cases. */
    public void replaceBlocks(java.util.List<ArtifactBlock> newBlocks) {
        this.graph = graph.withBlocks(newBlocks);
    }

    public String getOrgKey() {
        return orgKey;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public ArtifactGraph getGraph() {
        return graph;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
