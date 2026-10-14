package com.processpuzzle.core.definition;

import java.util.Optional;
import java.util.Set;

/**
 * Runs the {@link MigrationStep}s of one kind: changes the objects of a definition so that they fit its draft.
 *
 * <p>Implemented by the feature library that stores the objects, and collected by {@code base-release-backend}, which
 * runs every pending step in the release's transaction before it checks and releases — and in a transaction it rolls
 * back for the dry run. The obligations of {@link ReleaseParticipant} hold: {@link #apply} writes through the
 * transaction it is called in and does not commit it. A step is not a business event, so it publishes none.
 */
public interface MigrationParticipant {

    /** One of {@link DefinitionKinds}' kind constants. */
    String kind();

    /** The {@link MigrationStep#operation()}s this participant runs. */
    Set<String> operations();

    /**
     * Why {@code step} cannot run against the organization's released definitions and drafts as they stand — an
     * attribute it names no longer exists, a value that does not fit — or empty when it can. A step that cannot run
     * is skipped.
     */
    Optional<String> unusable(String orgKey, MigrationStep step);

    /**
     * Changes the objects as {@code step} says.
     *
     * @return how many objects it changed
     * @throws MigrationStepFailedException if some object cannot be changed as the step says; the release is then
     *                                      refused and rolled back
     */
    long apply(String orgKey, MigrationStep step);
}
