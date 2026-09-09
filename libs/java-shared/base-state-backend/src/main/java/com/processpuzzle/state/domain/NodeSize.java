package com.processpuzzle.state.domain;

/**
 * The dimensions of a node on the diagram canvas — ng-diagram's own {@code Size}.
 *
 * <p>Named {@code NodeSize} to match the contract schema of the same name, which could not be
 * called {@code Size}: the generated models share a package with the Jakarta validation
 * annotations the generator emits, so a {@code Size} schema shadows
 * {@code jakarta.validation.constraints.Size} and breaks every {@code @Size}-annotated field
 * around it.
 *
 * <p>Present only when a node has been resized away from its content-driven default — see
 * {@link NodeLayout#size()}.
 *
 * @param width  node width in diagram coordinates
 * @param height node height in diagram coordinates
 */
public record NodeSize(double width, double height) {
}
