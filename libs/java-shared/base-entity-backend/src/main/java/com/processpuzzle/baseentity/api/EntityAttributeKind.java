package com.processpuzzle.baseentity.api;

/**
 * The value kind of one attribute, as {@link EntityAttributeQuery} reports it.
 *
 * <p>A copy of the definition module's {@code ValueKind} rather than that enum itself: handing out
 * the internal enum would make every caller a dependant of the definition module's domain package,
 * and the whole point of this package is that they are not. The two are kept in step by name — see
 * {@code EntityAttributeQueryAdapter}, which converts by {@code valueOf} and therefore fails loudly
 * if a kind is added on one side only.
 */
public enum EntityAttributeKind {
    TEXT,
    NUMBER,
    BOOLEAN,
    DATE,
    DATE_TIME,
    ENUM,
    REFERENCE
}
