package com.processpuzzle.core.logging;

import org.slf4j.event.Level;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Traces entry and exit of the annotated method, with arguments and return value rendered as JSON.
 * Takes precedence over {@link LogClass} on the declaring type.
 *
 * <p><strong>The default level is DEBUG, and was INFO before 2026-09-21</strong> — see
 * {@link LogClass} for why it moved and how to turn the trace back on. An explicit
 * {@code @LogMethod(level = …)} is unaffected; only the default changed.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LogMethod {
    Level level() default Level.DEBUG;
}
