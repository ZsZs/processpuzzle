/**
 * The refusals the organization use cases raise, each mapped to an HTTP status and a stable
 * {@code errorId} by {@code PlatformAdminApiExceptionHandler}.
 *
 * <p>Exposed as the {@code exception} named interface, which is not how the sibling feature libraries
 * arrange theirs — they keep exceptions internal. The reason is specific to this module: base-app
 * still serves the five tenant-facing {@code /organizations*} operations and now raises <em>these</em>
 * types from them, and {@code @RestControllerAdvice(basePackages = ...)} matches on the package of the
 * <em>controller</em>, not of the exception. So {@code AppApiExceptionHandler} has to name these
 * classes itself, or those five endpoints would answer {@code 500 internal-error} instead of
 * {@code organization.not-found} and friends.
 *
 * <p>Two advices on the {@code FEATURE} rung therefore claim the same four types. That is allowed
 * precisely because their {@code basePackages} are disjoint — see {@code ApiAdviceOrder} and the
 * {@code ApiAdviceScopeTest} that enforces it.
 */
@NamedInterface("exception")
package com.processpuzzle.platformadmin.usecase.exception;

import org.springframework.modulith.NamedInterface;
