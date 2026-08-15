package com.processpuzzle.baseentity.definition.domain;

/**
 * Data type of an attribute's value. Drives JSON payload validation and RSQL comparison casting
 * on the instances side. TEXT_LIST is intentionally absent — cardinality is expressed via
 * {@link BaseEntityAttribute#isMultiValued()} instead of a parallel value kind.
 */
public enum ValueKind {
    TEXT,
    NUMBER,
    BOOLEAN,
    DATE,
    DATE_TIME,
    ENUM,
    REFERENCE
}
