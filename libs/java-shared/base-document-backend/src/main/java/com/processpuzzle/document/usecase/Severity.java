package com.processpuzzle.document.usecase;

/**
 * How badly a failed document check should be taken.
 *
 * <p>Deliberately this module's own enum, not base-rule's. It was base-rule's — three identical
 * constants, and reusing them cost base-document a compile dependency on the whole of
 * base-rule-backend. The OpenAPI contract already duplicated the {@code Severity} <em>schema</em>
 * per feature, so the two sides now agree rather than the Java half being coupled where the
 * contract half was not.
 *
 * <p>Duplication is the right call here beyond just breaking the edge: these are separate
 * vocabularies that happen to coincide today. A document check may one day want a severity a rule
 * verdict has no use for, and a shared enum would make that a change to base-rule.
 *
 * <p>Bridged into {@code document.model.Severity} by {@code DocumentMapper}, by name.
 */
public enum Severity {
    ERROR,
    WARNING,
    INFO
}
