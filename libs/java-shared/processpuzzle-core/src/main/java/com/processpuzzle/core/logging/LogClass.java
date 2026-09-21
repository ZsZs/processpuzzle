package com.processpuzzle.core.logging;

import org.slf4j.event.Level;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Traces entry and exit of every public method of the annotated type, with arguments and return
 * value rendered as JSON.
 *
 * <p><strong>The default level is DEBUG, and was INFO before 2026-09-21.</strong> This is a
 * behavioural change for everything that consumes this library, so it is worth stating plainly:
 * annotated types no longer trace anything under a default log configuration. Turn it back on where
 * it is wanted, per package or per class, without touching code:
 *
 * <pre>
 * logging.level.com.processpuzzle: DEBUG                        # everything
 * logging.level.com.processpuzzle.app.adapter.inbound: DEBUG    # one adapter
 * </pre>
 *
 * <p>Why it moved. What this produces is a full method trace: {@code LoggingAspect} serializes the
 * arguments AND the result of every call, writes each into the log message and again into the MDC,
 * and the logstash encoder then emits both. A single {@code createAppDefinition} at startup put its
 * whole input document into the log fifteen times over. That is exactly what DEBUG is for —
 * expensive detail you ask for while diagnosing something — and it is not what an operator reading
 * INFO wants to wade through. On a list endpoint the serialization is also proportional to the
 * response, so the cost grew with the data.
 *
 * <p>Errors are unaffected: {@code LoggingAspect} logs a thrown exception at ERROR regardless of
 * this setting, so demoting the trace never hides a failure.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface LogClass {
    Level level() default Level.DEBUG;
}
