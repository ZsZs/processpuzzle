package com.processpuzzle.workflow.definition.domain;

import com.processpuzzle.workflow.common.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The graphical layout of one {@link Workflow}: where its tasks, lanes, work products and tools sit
 * on the modeler's canvas, how the relations between them are routed, and where the canvas was panned
 * and zoomed to. Identified by the same ({@code orgKey}, {@code workflowId}) pair the workflow is —
 * see {@link WorkflowDiagramKey} — so exactly one layout exists per workflow.
 *
 * <p><strong>A resource of its own rather than fields on {@link Workflow}.</strong> Two reasons, both
 * practical: dragging a task would otherwise have to go through {@link ReplaceWorkflowUseCase}'s
 * whole-document replace of the composition, putting the workflow's semantics at risk on a purely
 * cosmetic gesture; and the two are written by different gestures at very different rates, so sharing
 * one {@code @Version} column would make an arrangement and an edit collide for no reason.
 *
 * <p>Purely presentational: nothing in {@code base-workflow} reads it. It exists so the modeler can
 * reproduce what the user arranged, and a workflow with no row here simply keeps the automatic
 * swimlane layout the frontend computes.
 *
 * <p>{@code nodes}, {@code edges} and {@code viewport} are JSONB columns, exactly as
 * {@link Workflow}'s four {@code ...Use} lists and its {@code startCondition} are, and for the same
 * reason: the whole layout stays in a single row, which is what makes {@link #getVersion()} a
 * meaningful optimistic-lock guard over the entire arrangement.
 *
 * <p>The table is {@code workflow_diagram}, singular, matching {@code workflow_definition}'s own
 * convention.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false, of = {"orgKey", "workflowId"})
@ToString
@Entity
@Table(name = "workflow_diagram")
@IdClass(WorkflowDiagramKey.class)
public class WorkflowDiagram extends Auditable {

    @Id
    @Column(name = "org_key", nullable = false)
    private String orgKey;

    @Id
    @Column(name = "workflow_id", nullable = false)
    private String workflowId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<DiagramNodeLayout> nodes = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<DiagramEdgeLayout> edges = new ArrayList<>();

    /** {@code null} until the canvas has been panned or zoomed. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private DiagramViewport viewport;

    @Version
    private Long version;

    /**
     * Whole-document replace of the layout — see {@code SaveWorkflowDiagramUseCase}. A {@code null}
     * list is a cleared layout rather than an untouched one, so it becomes empty rather than being
     * skipped; that is what makes the next save prune rows for nodes the workflow no longer has.
     */
    public void replaceLayout(List<DiagramNodeLayout> nodes, List<DiagramEdgeLayout> edges, DiagramViewport viewport) {
        this.nodes = nodes == null ? new ArrayList<>() : new ArrayList<>(nodes);
        this.edges = edges == null ? new ArrayList<>() : new ArrayList<>(edges);
        this.viewport = viewport;
    }
}
