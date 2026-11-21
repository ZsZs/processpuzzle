package com.processpuzzle.baseentity.unit.domain;

/**
 * A unit an organization added is either offered for new values, or retired: no longer offered, but still part of the
 * catalog, so values stored with it stay valid and keep their derived numbers.
 */
public enum UnitStatus {
    ACTIVE, RETIRED
}
