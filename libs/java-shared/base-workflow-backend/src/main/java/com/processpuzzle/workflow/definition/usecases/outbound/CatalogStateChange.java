package com.processpuzzle.workflow.definition.usecases.outbound;

/**
 * What a SYSTEM event of the catalog stands for when it is a state change: an object of {@code subjectType}
 * reaching {@code state} — or any state, when {@code state} is null.
 *
 * <p>Read by the release's state-coverage check: a catch of such an event on a task is how a workflow handles a
 * transition the task itself does not make.
 */
public record CatalogStateChange(String subjectType, String state) {

    /** Whether this change is the one that takes an object of {@code subjectType} into {@code targetState}. */
    public boolean reaches(String subjectType, String targetState) {
        return this.subjectType != null && this.subjectType.equalsIgnoreCase(subjectType)
                && (state == null || state.isBlank() || state.equalsIgnoreCase(targetState));
    }
}
