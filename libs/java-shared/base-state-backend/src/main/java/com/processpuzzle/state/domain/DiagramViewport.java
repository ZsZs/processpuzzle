package com.processpuzzle.state.domain;

/**
 * Pan offset and zoom of the modeler canvas, persisted so that reopening a large state machine
 * returns to the part of it the user was working on rather than to whatever an automatic fit
 * chooses.
 *
 * <p>Unlike {@link NodeLayout} and {@link EdgeLayout} this is not JSON-serialized: it is three
 * scalars, stored as three nullable columns on {@link DiagramDefinition}. A converter would buy
 * nothing, and columns keep the values readable in the database.
 *
 * @param x     horizontal pan offset
 * @param y     vertical pan offset
 * @param scale zoom factor, {@code 1.0} being 100%
 */
public record DiagramViewport(double x, double y, double scale) {
}
