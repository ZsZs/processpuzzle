package com.processpuzzle.document.domain;

/**
 * Lightweight type tag for an {@link DocumentInputPort} / {@link DocumentOutputPort} — UI
 * hinting and basic validation only. ENTITY_REF is a single base-entity instance;
 * ENTITY_COLLECTION is an RSQL-filtered set. Neither requires base-document to know the
 * entity's schema.
 */
public enum PortType {
    STRING,
    NUMBER,
    BOOLEAN,
    DATE,
    OBJECT,
    ARRAY,
    ENTITY_REF,
    ENTITY_COLLECTION
}
