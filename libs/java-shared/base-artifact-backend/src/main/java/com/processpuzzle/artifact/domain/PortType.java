package com.processpuzzle.artifact.domain;

/**
 * Lightweight type tag for an {@link ArtifactInputPort} / {@link ArtifactOutputPort} — UI
 * hinting and basic validation only. ENTITY_REF is a single base-entity instance;
 * ENTITY_COLLECTION is an RSQL-filtered set. Neither requires base-artifact to know the
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
