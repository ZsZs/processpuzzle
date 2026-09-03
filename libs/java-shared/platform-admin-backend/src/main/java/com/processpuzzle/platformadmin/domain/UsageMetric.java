package com.processpuzzle.platformadmin.domain;

/**
 * What a {@link UsageRecord} counts.
 *
 * <p>A closed enum rather than a free-text key, because a {@link Plan}'s limits are keyed by these
 * values: an unrecognised metric could not be checked against anything, so it would record a number
 * nobody ever reads. Adding one is a code change on purpose — it needs a collector to produce it and
 * a limit on each plan to mean anything.
 */
public enum UsageMetric {
    USERS,
    ENTITY_OBJECTS,
    DOCUMENTS,
    WORKFLOW_INSTANCES,
    STORAGE_BYTES,
    API_CALLS
}
