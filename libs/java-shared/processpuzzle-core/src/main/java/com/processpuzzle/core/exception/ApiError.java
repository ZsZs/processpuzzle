package com.processpuzzle.core.exception;

/**
 * The error body every ProcessPuzzle service returns: {@code {"errorId": "...", "errorText": "..."}},
 * as declared by {@code ErrorResponse} in {@code shared-api.yaml} and every feature contract.
 *
 * <p>{@code errorId} is a stable, machine-readable identifier a client can key on — dotted and
 * namespaced by feature ({@code document.slug.already-exists}, {@code request.invalid-argument}) — and
 * is also usable as a Transloco key on the frontend. {@code errorText} is the human-readable fallback
 * in the service's default language.
 *
 * <p><b>Why this duplicates the generated {@code com.processpuzzle.shared.model.ErrorResponse}:</b>
 * that class is generated from {@code shared-api.yaml} into the {@code api-contracts} artifact, and
 * {@code processpuzzle-core} does not depend on it — core is the lowest layer, which every feature
 * library builds on, and making it depend on the generated API models to name two strings would invert
 * that. Feature modules, which already have the contracts dependency, use the generated type instead
 * (see {@code AppApiExceptionHandler} and {@code DocumentApiExceptionHandler}). The two serialize
 * identically, and that is the property that matters: the wire shape is single, the Java type is
 * per-layer. Please do not "unify" these by adding api-contracts to core.
 */
public record ApiError(String errorId, String errorText) {
}
