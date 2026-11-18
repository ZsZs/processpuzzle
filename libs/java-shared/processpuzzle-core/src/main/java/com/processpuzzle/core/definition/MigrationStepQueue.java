package com.processpuzzle.core.definition;

import java.util.List;

/**
 * Where an organization's pending {@link MigrationStep}s are kept until its next release runs them. Implemented by
 * base-release, for server-side code that brings steps with it: a starter upgrade, whose bundle says how the objects
 * of the version before it must change to fit the definitions of this one. Joins the caller's transaction, so a dry run
 * or a refused upgrade leaves no step behind.
 */
public interface MigrationStepQueue {

    /**
     * Appends {@code steps} to the organization's pending steps, after the ones its designer already queued.
     *
     * @throws IllegalArgumentException if a step names a kind or an operation that nothing runs; nothing is queued
     */
    void enqueue(String orgKey, List<MigrationStep> steps);
}
