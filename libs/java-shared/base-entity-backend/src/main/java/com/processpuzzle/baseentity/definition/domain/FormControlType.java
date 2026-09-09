package com.processpuzzle.baseentity.definition.domain;

/**
 * UI rendering hint consumed by the frontend form generator.
 * Mirrors libs/js-shared/base-entity-frontend abstact-attr.descriptor.ts FormControlType.
 */
public enum FormControlType {
    TEXT,
    TEXTAREA,
    TEXT_BOX,
    NUMBER,
    DATE,
    DATE_TIME,
    BOOLEAN,
    CHECKBOX,
    RADIO,
    DROPDOWN,
    ENUM_SELECT,
    FOREIGN_KEY,
    LOOKUP,
    EMBEDDED_COMPONENTS,
    COMPONENTS,
    RELATED_ENTITIES,
    ARTIFACT,
    TAGS,
    TITLE,
    LABEL,
    ADDITIONAL_PROPERTIES,
    FLEX_BOX
}
