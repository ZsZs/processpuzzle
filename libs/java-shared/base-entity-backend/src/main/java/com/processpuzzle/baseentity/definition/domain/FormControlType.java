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
    /**
     * An amount of a unit of one quantity kind: valueKind REFERENCE, single-valued, no linkedEntityType. The value is
     * {@code {amount, unit}} with the amount as decimal text and the unit a code of the standard catalog; the backend
     * adds {@code baseAmount}, the amount in the kind's base unit, so values compare across units. May set quantityKind,
     * allowedUnits, defaultUnit and scale.
     */
    QUANTITY,
    /**
     * An amount of a currency: valueKind REFERENCE, single-valued, no linkedEntityType. The value is
     * {@code {amount, currency}} with an ISO 4217 currency; the amount has at most the currency's minor units as decimal
     * places and the backend adds {@code baseAmount}, the same amount as a number. May set allowedCurrencies and
     * defaultCurrency.
     */
    MONEY,
    /**
     * Two or three lengths in one unit: valueKind REFERENCE, single-valued, no linkedEntityType. The value is
     * {@code {width, height, depth?, unit}}; the backend adds {@code baseArea} and, for three axes, {@code baseVolume},
     * in square and cubic metres. May set allowedUnits (lengths), defaultUnit, scale and dimensionCount.
     */
    DIMENSIONS,
    /**
     * A value a base-calculation calculation derives: single-valued, a scalar valueKind (NUMBER, TEXT, DATE or
     * BOOLEAN), no linkedEntityType. The form shows it read-only, recomputed from the unsaved form state.
     */
    CALCULATED,
    TAGS,
    TITLE,
    LABEL,
    ADDITIONAL_PROPERTIES,
    FLEX_BOX
}
