package com.processpuzzle.state.domain;

/**
 * Where one {@link State} sits on the graphical modeler's canvas.
 *
 * <p>Persisted as an element of {@link DiagramDefinition#getNodes()}, JSON-serialized via
 * {@link NodeLayoutsConverter} — the same treatment {@link State} itself gets inside
 * {@code StateMachineDefinition}, and for the same reason: a node layout has no identity or
 * lifecycle of its own, and a whole-document replace is the only way it ever changes.
 *
 * <p>{@code stateKey} is deliberately <em>not</em> checked against the state machine's declared
 * states — see {@code SaveDiagramDefinition} for why a stale row is tolerated rather than
 * rejected.
 *
 * @param stateKey the {@link State#key()} this row positions
 * @param position the node's top-left corner in diagram coordinates
 * @param size     explicit dimensions; {@code null} when the node is auto-sized by its content,
 *                 which is the default
 */
public record NodeLayout(String stateKey, Point position, NodeSize size) {

    public NodeLayout {
        if (stateKey == null || stateKey.isBlank()) {
            throw new IllegalArgumentException("NodeLayout.stateKey must not be blank");
        }
        if (position == null) {
            throw new IllegalArgumentException("NodeLayout.position must not be null");
        }
    }
}
