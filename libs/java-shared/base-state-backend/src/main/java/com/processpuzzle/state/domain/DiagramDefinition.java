package com.processpuzzle.state.domain;

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
import java.util.ArrayList;
import java.util.List;

/**
 * The graphical layout of one {@link StateMachineDefinition}: where its states sit on the
 * modeler's canvas, how its transitions are routed, and where the canvas was panned and zoomed
 * to. Identified by the same ({@code orgKey}, {@code entityName}) pair the state machine is — see
 * {@link DiagramDefinitionKey} — so exactly one layout exists per machine.
 *
 * <p><strong>A resource of its own rather than fields on {@link StateMachineDefinition}.</strong>
 * Two reasons, both practical: dragging a node would otherwise have to go through
 * {@code UpdateStateMachineDefinition}'s whole-document replace of the topology, putting the
 * machine's semantics at risk on a purely cosmetic gesture; and the two are written by different
 * gestures at very different rates, so sharing one {@code @Version} column would make an
 * arrangement and an edit collide for no reason.
 *
 * <p>Purely presentational: nothing in {@code base-state} reads it. It exists so the modeler can
 * reproduce what the user arranged, and a machine with no row here simply falls back to an
 * automatic layout ({@code DagreLayoutService} / {@code ElkLayoutService} on the frontend).
 *
 * <p>{@code nodes} and {@code edges} are serialized JSON blobs in portable {@code @Lob} text
 * columns, exactly as {@link StateMachineDefinition}'s {@code states}/{@code transitions} are, and
 * for the same reasons — see that class's javadoc for why a converter beats
 * {@code @ElementCollection} and why the column is not a Postgres-specific {@code jsonb}. The
 * viewport, being three scalars rather than a collection, is three plain nullable columns instead:
 * a converter would buy nothing.
 */
@Entity
@Table(name = "diagram_definitions")
@IdClass(DiagramDefinitionKey.class)
public class DiagramDefinition {

    @Id
    @Column(name = "org_key", length = 63)
    private String orgKey;

    @Id
    @Column(name = "entity_name", length = 100)
    private String entityName;

    @Lob
    @Convert(converter = NodeLayoutsConverter.class)
    @Column(nullable = false)
    private List<NodeLayout> nodes = new ArrayList<>();

    @Lob
    @Convert(converter = EdgeLayoutsConverter.class)
    @Column(nullable = false)
    private List<EdgeLayout> edges = new ArrayList<>();

    @Column(name = "viewport_x")
    private Double viewportX;

    @Column(name = "viewport_y")
    private Double viewportY;

    @Column(name = "viewport_scale")
    private Double viewportScale;

    @Version
    private Long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected DiagramDefinition() {
        // required by JPA
    }

    private DiagramDefinition(Builder builder) {
        this.orgKey = builder.orgKey;
        this.entityName = builder.entityName;
        this.nodes = builder.nodes == null ? new ArrayList<>() : new ArrayList<>(builder.nodes);
        this.edges = builder.edges == null ? new ArrayList<>() : new ArrayList<>(builder.edges);
        applyViewport(builder.viewport);
    }

    public static Builder builder() {
        return new Builder();
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

    /** The owning organization. Part of the identity, so there is deliberately no setter. */
    public String getOrgKey() {
        return orgKey;
    }

    /** The entity type whose state machine this lays out. Part of the identity, so no setter. */
    public String getEntityName() {
        return entityName;
    }

    public List<NodeLayout> getNodes() {
        return List.copyOf(nodes);
    }

    public List<EdgeLayout> getEdges() {
        return List.copyOf(edges);
    }

    /**
     * The persisted viewport, or {@code null} when the canvas has never been panned or zoomed.
     * All three columns are written and cleared together, so a partially-set viewport is not
     * representable; {@code null} in any of them is read as "no viewport".
     */
    public DiagramViewport getViewport() {
        if (viewportX == null || viewportY == null || viewportScale == null) {
            return null;
        }
        return new DiagramViewport(viewportX, viewportY, viewportScale);
    }

    /** Whole-document replace of the layout — see {@code SaveDiagramDefinition}. */
    public void replaceLayout(List<NodeLayout> nodes, List<EdgeLayout> edges, DiagramViewport viewport) {
        this.nodes = nodes == null ? new ArrayList<>() : new ArrayList<>(nodes);
        this.edges = edges == null ? new ArrayList<>() : new ArrayList<>(edges);
        applyViewport(viewport);
    }

    private void applyViewport(DiagramViewport viewport) {
        this.viewportX = viewport == null ? null : viewport.x();
        this.viewportY = viewport == null ? null : viewport.y();
        this.viewportScale = viewport == null ? null : viewport.scale();
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

    public static class Builder {
        private String orgKey;
        private String entityName;
        private List<NodeLayout> nodes = new ArrayList<>();
        private List<EdgeLayout> edges = new ArrayList<>();
        private DiagramViewport viewport;

        public Builder orgKey(String orgKey) {
            this.orgKey = orgKey;
            return this;
        }

        public Builder entityName(String entityName) {
            this.entityName = entityName;
            return this;
        }

        public Builder nodes(List<NodeLayout> nodes) {
            this.nodes = nodes;
            return this;
        }

        public Builder edges(List<EdgeLayout> edges) {
            this.edges = edges;
            return this;
        }

        public Builder viewport(DiagramViewport viewport) {
            this.viewport = viewport;
            return this;
        }

        public DiagramDefinition build() {
            return new DiagramDefinition(this);
        }
    }
}
