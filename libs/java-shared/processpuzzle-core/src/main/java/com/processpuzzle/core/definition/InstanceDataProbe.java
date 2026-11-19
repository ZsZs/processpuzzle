package com.processpuzzle.core.definition;

import java.util.Collection;

/**
 * Counts one kind of instance data an organization holds — entity objects, workflow instances — so
 * that installing a starter can refuse to replace definitions that live data still refers to, and
 * uninstalling one can refuse to remove definitions that still have data.
 *
 * <p>Implemented by each feature that keeps instance data, and collected by {@code base-starter-backend}
 * alongside the {@link DefinitionImportParticipant}s.
 */
public interface InstanceDataProbe {

    /** What is counted, in the plural, for a refusal message: {@code "entity objects"}. */
    String label();

    /** Everything of this kind the organization holds, whichever definition it belongs to. */
    long count(String orgKey);

    /**
     * The {@link DefinitionKinds} kind of the definitions whose instances this probe counts — {@code entity} for
     * entity objects — or null for a probe that cannot tell them apart and answers {@link #count(String)} only.
     */
    default String definitionKind() {
        return null;
    }

    /**
     * The instances that belong to the definitions {@code definitionKeys} of {@link #definitionKind()}: the keyed form
     * that lets one starter's data be told from the organization's other data. A probe without a
     * {@link #definitionKind()} counts all of the organization's data, the safe answer for a caller that asks.
     */
    default long count(String orgKey, Collection<String> definitionKeys) {
        return count(orgKey);
    }
}
