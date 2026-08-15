package com.processpuzzle.baseentity.definition.domain;

/**
 * UI rendering hint consumed by the frontend form generator.
 * TODO reconcile against libs/js-shared/base-entity-frontend/.../abstact-attr.descriptor.ts
 * FormControlType — only FOREIGN_KEY / EMBEDDED_COMPONENTS / ARTIFACT are confirmed against
 * that source; the rest are placeholders.
 */
public enum FormControlType {
    TEXT,
    TEXTAREA,
    NUMBER,
    DATE,
    DATE_TIME,
    BOOLEAN,
    ENUM_SELECT,
    FOREIGN_KEY,
    EMBEDDED_COMPONENTS,
    ARTIFACT
}
