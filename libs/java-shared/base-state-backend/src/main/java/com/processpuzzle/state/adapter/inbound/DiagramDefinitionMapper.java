package com.processpuzzle.state.adapter.inbound;

import com.processpuzzle.state.domain.DiagramViewport;
import com.processpuzzle.state.domain.EdgeLayout;
import com.processpuzzle.state.domain.NodeLayout;
import com.processpuzzle.state.domain.NodeSize;
import com.processpuzzle.state.domain.Point;
import com.processpuzzle.state.model.DiagramDefinitionInput;
import com.processpuzzle.state.model.PageOfDiagramDefinition;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/**
 * Translates between the generated diagram shapes and {@link com.processpuzzle.state.domain
 * .DiagramDefinition}, mirroring {@link StateMapper}'s discipline: no validation and no computation
 * here, only mapping.
 *
 * <p>Coordinates unbox without a null check because the contract marks every one of them required,
 * so the generated DTOs carry {@code @NotNull} and Spring's {@code @Valid @RequestBody} answers
 * {@code 400} for a half-specified point before this class is reached.
 */
@Component
public class DiagramDefinitionMapper {

    /**
     * {@code orgKey} and {@code entityName} both come from the path. Unlike {@link
     * StateMapper#toDomain}, which reads {@code entityName} out of the body on create, this one
     * ignores {@link DiagramDefinitionInput#getEntityName()} entirely: the diagram is always
     * addressed by an {@code entityName} that must already have a state machine, so the body can
     * only ever repeat or contradict the URL.
     */
    public com.processpuzzle.state.domain.DiagramDefinition toDomain(
            String orgKey, String entityName, DiagramDefinitionInput input) {
        return com.processpuzzle.state.domain.DiagramDefinition.builder()
                .orgKey(orgKey)
                .entityName(entityName)
                .nodes(toDomainNodes(input.getNodes()))
                .edges(toDomainEdges(input.getEdges()))
                .viewport(toDomainViewport(input.getViewport()))
                .build();
    }

    public com.processpuzzle.state.model.DiagramDefinition toModel(
            com.processpuzzle.state.domain.DiagramDefinition definition) {
        com.processpuzzle.state.model.DiagramDefinition model =
                new com.processpuzzle.state.model.DiagramDefinition(definition.getEntityName());
        model.setNodes(toModelNodes(definition.getNodes()));
        model.setEdges(toModelEdges(definition.getEdges()));
        model.setViewport(toModelViewport(definition.getViewport()));
        model.setOrgKey(definition.getOrgKey());
        model.setVersion(definition.getVersion());
        model.setCreatedAt(toOffsetDateTime(definition.getCreatedAt()));
        model.setUpdatedAt(toOffsetDateTime(definition.getUpdatedAt()));
        return model;
    }

    public PageOfDiagramDefinition toModel(Page<com.processpuzzle.state.domain.DiagramDefinition> page) {
        List<com.processpuzzle.state.model.DiagramDefinition> content =
                page.getContent().stream().map(this::toModel).toList();
        return new PageOfDiagramDefinition()
                .content(content)
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .number(page.getNumber())
                .size(page.getSize());
    }

    // ── to domain ──────────────────────────────────────────────

    private List<NodeLayout> toDomainNodes(List<com.processpuzzle.state.model.NodeLayout> nodes) {
        if (nodes == null) {
            return List.of();
        }
        return nodes.stream()
                .map(n -> new NodeLayout(n.getStateKey(), toDomainPoint(n.getPosition()), toDomainSize(n.getSize())))
                .toList();
    }

    private List<EdgeLayout> toDomainEdges(List<com.processpuzzle.state.model.EdgeLayout> edges) {
        if (edges == null) {
            return List.of();
        }
        return edges.stream()
                .map(e -> new EdgeLayout(
                        e.getTransitionKey(),
                        toDomainPoints(e.getPoints()),
                        e.getSourcePort(),
                        e.getTargetPort(),
                        e.getRouting()))
                .toList();
    }

    private List<Point> toDomainPoints(List<com.processpuzzle.state.model.Point> points) {
        if (points == null) {
            return List.of();
        }
        return points.stream().map(this::toDomainPoint).toList();
    }

    private Point toDomainPoint(com.processpuzzle.state.model.Point point) {
        return point == null ? null : new Point(point.getX(), point.getY());
    }

    private NodeSize toDomainSize(com.processpuzzle.state.model.NodeSize size) {
        return size == null ? null : new NodeSize(size.getWidth(), size.getHeight());
    }

    private DiagramViewport toDomainViewport(com.processpuzzle.state.model.DiagramViewport viewport) {
        return viewport == null ? null
                : new DiagramViewport(viewport.getX(), viewport.getY(), viewport.getScale());
    }

    // ── to model ───────────────────────────────────────────────

    private List<com.processpuzzle.state.model.NodeLayout> toModelNodes(List<NodeLayout> nodes) {
        return nodes.stream()
                .map(n -> new com.processpuzzle.state.model.NodeLayout(n.stateKey(), toModelPoint(n.position()))
                        .size(toModelSize(n.size())))
                .toList();
    }

    private List<com.processpuzzle.state.model.EdgeLayout> toModelEdges(List<EdgeLayout> edges) {
        return edges.stream()
                .map(e -> new com.processpuzzle.state.model.EdgeLayout(e.transitionKey())
                        .points(e.points().stream().map(this::toModelPoint).toList())
                        .sourcePort(e.sourcePort())
                        .targetPort(e.targetPort())
                        .routing(e.routing()))
                .toList();
    }

    private com.processpuzzle.state.model.Point toModelPoint(Point point) {
        return new com.processpuzzle.state.model.Point(point.x(), point.y());
    }

    private com.processpuzzle.state.model.NodeSize toModelSize(NodeSize size) {
        return size == null ? null : new com.processpuzzle.state.model.NodeSize(size.width(), size.height());
    }

    private com.processpuzzle.state.model.DiagramViewport toModelViewport(DiagramViewport viewport) {
        return viewport == null ? null
                : new com.processpuzzle.state.model.DiagramViewport(viewport.x(), viewport.y(), viewport.scale());
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
