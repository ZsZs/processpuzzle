package com.processpuzzle.state.domain;

import java.util.List;

/**
 * How one {@link Transition} is drawn on the graphical modeler's canvas.
 *
 * <p>The port anchors matter as much as the waypoints do: ng-diagram picks an anchor when an edge
 * is created, so an edge whose ports are not persisted is re-anchored on the next load and the
 * diagram reopens with visibly different geometry from the one the user arranged.
 *
 * <p>{@code transitionKey} is deliberately <em>not</em> checked against the state machine's
 * declared transitions — see {@code SaveDiagramDefinition}.
 *
 * @param transitionKey the {@link Transition#key()} this row routes
 * @param points        intermediate waypoints, in order; empty means the edge is routed
 *                      automatically between its two ports
 * @param sourcePort    the port on the source node the edge leaves from, e.g. {@code port-right}
 * @param targetPort    the port on the target node the edge arrives at, e.g. {@code port-left}
 * @param routing       the edge's routing mode as the user chose it — ng-diagram's
 *                      {@code Edge.routing}. Opaque here: stored and returned without
 *                      interpretation, so a new ng-diagram routing mode needs no backend change.
 */
public record EdgeLayout(
        String transitionKey,
        List<Point> points,
        String sourcePort,
        String targetPort,
        String routing
) {

    public EdgeLayout {
        if (transitionKey == null || transitionKey.isBlank()) {
            throw new IllegalArgumentException("EdgeLayout.transitionKey must not be blank");
        }
        points = points == null ? List.of() : List.copyOf(points);
    }
}
