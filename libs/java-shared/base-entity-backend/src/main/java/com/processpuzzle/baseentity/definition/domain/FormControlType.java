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
    AVATAR,
    /**
     * An append-only history of rich-text comments: valueKind REFERENCE, multi-valued, no linkedEntityType.
     * Each entry is {@code {author, authorId?, createdAt, content}} with Tiptap JSON content.
     */
    COMMENTS,
    /**
     * A postal address: valueKind REFERENCE, single-valued, no linkedEntityType. The value is
     * {@code {country?, street?, addressLine2?, postalCode?, city?, region?, latitude?, longitude?, locationSource?}}
     * with an ISO 3166-1 alpha-2 country. Only this control may set showMap, mapProvider and defaultCountry.
     */
    ADDRESS,
    /**
     * Where the device was when the user pressed the control's button: valueKind REFERENCE, single-valued, no
     * linkedEntityType. The value is {@code {latitude, longitude, accuracy?, capturedAt?}}, read from the browser.
     */
    GEO_LOCATION,
    TAGS,
    TITLE,
    LABEL,
    ADDITIONAL_PROPERTIES,
    FLEX_BOX
}
