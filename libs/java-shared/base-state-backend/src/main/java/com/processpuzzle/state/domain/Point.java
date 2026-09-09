package com.processpuzzle.state.domain;

/**
 * A point in diagram coordinates — ng-diagram's own {@code Point}, and the unit both a node's
 * position and an edge's waypoints are expressed in.
 *
 * <p>Primitive {@code double}s rather than boxed {@code Double}s: the contract marks {@code x} and
 * {@code y} required, so the generated DTO carries {@code @NotNull} on both and Spring's
 * {@code @Valid @RequestBody} rejects a null coordinate with a {@code 400} before any mapping runs.
 * Nothing downstream has to defend against a half-specified point.
 *
 * @param x horizontal offset in diagram coordinates
 * @param y vertical offset in diagram coordinates
 */
public record Point(double x, double y) {
}
