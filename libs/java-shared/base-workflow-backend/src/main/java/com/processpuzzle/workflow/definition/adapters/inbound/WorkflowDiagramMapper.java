package com.processpuzzle.workflow.definition.adapters.inbound;

import com.processpuzzle.workflow.definition.domain.DiagramEdgeLayout;
import com.processpuzzle.workflow.definition.domain.DiagramNodeLayout;
import com.processpuzzle.workflow.definition.domain.DiagramNodeSize;
import com.processpuzzle.workflow.definition.domain.DiagramPoint;
import com.processpuzzle.workflow.definition.domain.DiagramViewport;
import com.processpuzzle.workflow.model.PageOfWorkflowDiagram;
import com.processpuzzle.workflow.model.WorkflowDiagramInput;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/**
 * Translates between the generated diagram shapes and
 * {@link com.processpuzzle.workflow.definition.domain.WorkflowDiagram}, mirroring
 * {@link WorkflowDefinitionMapper}'s discipline: no validation and no computation here, only mapping.
 *
 * <p>Coordinates unbox without a null check because the contract marks every one of them required, so
 * the generated DTOs carry {@code @NotNull} and Spring's {@code @Valid @RequestBody} answers
 * {@code 400} for a half-specified point before this class is reached.
 */
@Component
public class WorkflowDiagramMapper {

    /**
     * {@code orgKey} and {@code workflowId} both come from the path. {@link
     * WorkflowDiagramInput#getWorkflowId()} is ignored entirely: the diagram is always addressed by a
     * {@code workflowId} that must already have a workflow, so the body can only ever repeat or
     * contradict the URL.
     */
    public com.processpuzzle.workflow.definition.domain.WorkflowDiagram toDomain(
            String orgKey, String workflowId, WorkflowDiagramInput input) {
        return com.processpuzzle.workflow.definition.domain.WorkflowDiagram.builder()
                .orgKey(orgKey)
                .workflowId(workflowId)
                .nodes(toDomainNodes(input.getNodes()))
                .edges(toDomainEdges(input.getEdges()))
                .viewport(toDomainViewport(input.getViewport()))
                // The version the caller last read, carried so SaveWorkflowDiagramUseCase can refuse a
                // stale write. Never persisted from here — Hibernate owns the column on the loaded row.
                .version(input.getVersion())
                .build();
    }

    public com.processpuzzle.workflow.model.WorkflowDiagram toModel(
            com.processpuzzle.workflow.definition.domain.WorkflowDiagram diagram) {
        return new com.processpuzzle.workflow.model.WorkflowDiagram(diagram.getWorkflowId())
                .nodes(toModelNodes(diagram.getNodes()))
                .edges(toModelEdges(diagram.getEdges()))
                .viewport(toModelViewport(diagram.getViewport()))
                .orgKey(diagram.getOrgKey())
                .version(diagram.getVersion())
                .createdAt(toOffsetDateTime(diagram.getCreatedAt()))
                .updatedAt(toOffsetDateTime(diagram.getUpdatedAt()));
    }

    public PageOfWorkflowDiagram toModel(Page<com.processpuzzle.workflow.definition.domain.WorkflowDiagram> page) {
        List<com.processpuzzle.workflow.model.WorkflowDiagram> content =
                page.getContent().stream().map(this::toModel).toList();
        return new PageOfWorkflowDiagram()
                .content(content)
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .number(page.getNumber())
                .size(page.getSize());
    }

    // ── to domain ──────────────────────────────────────────────

    private List<DiagramNodeLayout> toDomainNodes(List<com.processpuzzle.workflow.model.DiagramNodeLayout> nodes) {
        if (nodes == null) {
            return List.of();
        }
        return nodes.stream()
                .map(n -> DiagramNodeLayout.builder()
                        .nodeId(n.getNodeId())
                        .position(toDomainPoint(n.getPosition()))
                        .size(toDomainSize(n.getSize()))
                        .build())
                .toList();
    }

    private List<DiagramEdgeLayout> toDomainEdges(List<com.processpuzzle.workflow.model.DiagramEdgeLayout> edges) {
        if (edges == null) {
            return List.of();
        }
        return edges.stream()
                .map(e -> DiagramEdgeLayout.builder()
                        .edgeId(e.getEdgeId())
                        .points(toDomainPoints(e.getPoints()))
                        .sourcePort(e.getSourcePort())
                        .targetPort(e.getTargetPort())
                        .routing(e.getRouting())
                        .build())
                .toList();
    }

    private List<DiagramPoint> toDomainPoints(List<com.processpuzzle.workflow.model.Point> points) {
        if (points == null) {
            return List.of();
        }
        return points.stream().map(this::toDomainPoint).toList();
    }

    private DiagramPoint toDomainPoint(com.processpuzzle.workflow.model.Point point) {
        return point == null ? null : DiagramPoint.builder().x(point.getX()).y(point.getY()).build();
    }

    private DiagramNodeSize toDomainSize(com.processpuzzle.workflow.model.NodeSize size) {
        return size == null ? null
                : DiagramNodeSize.builder().width(size.getWidth()).height(size.getHeight()).build();
    }

    private DiagramViewport toDomainViewport(com.processpuzzle.workflow.model.DiagramViewport viewport) {
        return viewport == null ? null
                : DiagramViewport.builder().x(viewport.getX()).y(viewport.getY()).scale(viewport.getScale()).build();
    }

    // ── to model ───────────────────────────────────────────────

    private List<com.processpuzzle.workflow.model.DiagramNodeLayout> toModelNodes(List<DiagramNodeLayout> nodes) {
        return nodes.stream()
                .map(n -> new com.processpuzzle.workflow.model.DiagramNodeLayout(n.getNodeId(), toModelPoint(n.getPosition()))
                        .size(toModelSize(n.getSize())))
                .toList();
    }

    private List<com.processpuzzle.workflow.model.DiagramEdgeLayout> toModelEdges(List<DiagramEdgeLayout> edges) {
        return edges.stream()
                .map(e -> new com.processpuzzle.workflow.model.DiagramEdgeLayout(e.getEdgeId())
                        .points(toModelPoints(e.getPoints()))
                        .sourcePort(e.getSourcePort())
                        .targetPort(e.getTargetPort())
                        .routing(e.getRouting()))
                .toList();
    }

    private List<com.processpuzzle.workflow.model.Point> toModelPoints(List<DiagramPoint> points) {
        return points == null ? List.of() : points.stream().map(this::toModelPoint).toList();
    }

    private com.processpuzzle.workflow.model.Point toModelPoint(DiagramPoint point) {
        return point == null ? null : new com.processpuzzle.workflow.model.Point(point.getX(), point.getY());
    }

    private com.processpuzzle.workflow.model.NodeSize toModelSize(DiagramNodeSize size) {
        return size == null ? null : new com.processpuzzle.workflow.model.NodeSize(size.getWidth(), size.getHeight());
    }

    private com.processpuzzle.workflow.model.DiagramViewport toModelViewport(DiagramViewport viewport) {
        return viewport == null ? null
                : new com.processpuzzle.workflow.model.DiagramViewport(viewport.getX(), viewport.getY(), viewport.getScale());
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
