package com.processpuzzle.core.exception;

import org.springframework.core.Ordered;

/**
 * Precedence of the {@code @RestControllerAdvice} classes that turn an exception into an error body.
 *
 * <p>Spring walks the advices in order and answers with the <em>first</em> one that has any handler
 * matching the exception — it does not pick the most specific handler across advices, only within one.
 * Unordered advices all sit at {@link Ordered#LOWEST_PRECEDENCE}, and ties between them are resolved
 * arbitrarily. A catch-all in an unordered advice therefore shadows every feature advice, whichever way
 * the tie happens to fall.
 *
 * <p>That is not hypothetical: it is what {@code ApiExceptionHandler}'s catch-all did on 2026-08-12,
 * when a duplicate slug and an unknown document both came back as {@code 500 internal-error} instead of
 * their own ids. Hence three explicit rungs, and an advice that declares none of them is a bug waiting
 * for a bean-ordering change.
 *
 * <h2>Order is not enough on its own: scope the advice too</h2>
 *
 * <p>A rung only disambiguates advices on <em>different</em> rungs. Every feature advice sits on
 * {@link #FEATURE}, so two of them claiming one exception type is a tie that order cannot break —
 * and the exception types most likely to be claimed twice are the ones no feature owns: Spring's
 * {@code OptimisticLockingFailureException}, {@code IllegalArgumentException}. Three advices claimed
 * optimistic-lock failures at this rung, which is how a stale write on a <em>workflow</em> row came
 * back as {@code document.stale-write}: the tie fell base-document's way, and it would have fallen
 * differently on a different classpath order.
 *
 * <p>So a feature advice declares the package its own endpoints live in:
 *
 * <pre>{@code @RestControllerAdvice(basePackages = "com.processpuzzle.workflow")}</pre>
 *
 * <p>Now the tie cannot arise — the scopes are disjoint, and each module answers for its own
 * endpoints with its own error ids. Only the two advices in this package are deliberately global,
 * because a generic refusal and an unhandled failure are the same for every module.
 * {@code ApiAdviceScopeTest} in the application module enforces both halves of this: that every
 * advice is scoped or intentionally global, and that no two advices sharing a rung and a scope claim
 * the same exception type.
 */
public final class ApiAdviceOrder {

    /** A feature's own exceptions ({@code document.*}, {@code rule.*}, {@code app.*}) — most specific, so first. */
    public static final int FEATURE = 100;

    /** Core's cross-cutting exceptions: bad argument, illegal state, unparseable payload, validation. */
    public static final int GENERIC = 200;

    /** {@code UnhandledExceptionHandler} only. Must be last, or it answers for everyone. */
    public static final int CATCH_ALL = Ordered.LOWEST_PRECEDENCE;

    private ApiAdviceOrder() {
    }
}
