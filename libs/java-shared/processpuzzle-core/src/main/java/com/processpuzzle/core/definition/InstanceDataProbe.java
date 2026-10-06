package com.processpuzzle.core.definition;

/**
 * Counts one kind of instance data an organization holds — entity objects, workflow instances — so
 * that installing a starter can refuse to replace definitions that live data still refers to.
 *
 * <p>Implemented by each feature that keeps instance data, and collected by {@code base-starter-backend}
 * alongside the {@link DefinitionImportParticipant}s.
 */
public interface InstanceDataProbe {

    /** What is counted, in the plural, for a refusal message: {@code "entity objects"}. */
    String label();

    long count(String orgKey);
}
