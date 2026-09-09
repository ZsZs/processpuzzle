package com.processpuzzle.app.usecase;

/**
 * How badly a failed app-definition check should be taken.
 *
 * <p>This module's own enum rather than base-rule's, for the reason given on
 * {@code document.usecase.Severity}: three identical constants are not worth a compile dependency
 * between two feature libraries, and the OpenAPI contract already duplicates the schema per feature.
 *
 * <p>A problem here may originate either from {@code AppDefinitionValidator}'s structural checks or
 * from an evaluated {@code base-rule} record. {@code AppRuleValidator} translates the latter's
 * severity by name — which is exactly the mapping an adapter would perform if base-rule were a
 * separate service, and is why the enums being distinct types is a feature rather than a nuisance.
 */
public enum Severity {
    ERROR,
    WARNING,
    INFO
}
