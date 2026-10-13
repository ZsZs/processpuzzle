package com.processpuzzle.baseentity.definition.domain;

/**
 * A definition's lifecycle, as its designer decides it. Whether it is live is not this but its release: the running
 * application reads the released row (see core's {@code docs/definition-release.md}).
 */
public enum EntityDefinitionStatus {
    /**
     * Before releases existed, what kept a definition off the running application. A release does that now, so a
     * definition is never stored as one: {@link BaseEntityDefinition} saves it as {@link #ACTIVE}, and
     * {@code EntityDefinitionStatusMigration} moved the rows that were.
     */
    @Deprecated
    DRAFT,
    /** In use. */
    ACTIVE,
    /** No longer offered by the running application. */
    DEPRECATED
}
