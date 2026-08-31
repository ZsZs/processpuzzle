package com.processpuzzle.workflow.definition.domain;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * How one relation is drawn.
 *
 * <p>The port anchors matter as much as the waypoints do. The frontend's flow converter pins them per
 * relation kind — the sequence flow runs left to right along the lanes, data and tool lines run
 * vertically into a strip below them — so an edge whose ports are not persisted is re-anchored on the
 * next load, and a data line can end up leaving a task by its right edge and crossing the whole chain.
 *
 * <p>{@link #routing} is ng-diagram's own routing mode, stored and returned without interpretation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagramEdgeLayout {

    private String edgeId;

    /** Intermediate waypoints, in order. Empty means the edge is routed automatically. */
    @Builder.Default
    private List<DiagramPoint> points = new ArrayList<>();

    private String sourcePort;
    private String targetPort;
    private String routing;
}
