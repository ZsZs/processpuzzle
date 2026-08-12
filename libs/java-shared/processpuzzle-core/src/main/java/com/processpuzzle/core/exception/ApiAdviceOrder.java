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
