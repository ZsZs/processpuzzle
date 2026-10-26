package com.processpuzzle.core.definition;

import java.util.List;
import java.util.Optional;

/**
 * Lets a client author the drafts of one definition kind as JSON: the shape a designer's form edits, and what the
 * definition generator of {@code docs/ai/implementation-plan.md} writes.
 *
 * <p>Implemented by each feature library for its own definitions, next to its import and release participants. Every
 * write goes to the {@link Channel#DRAFT} and nowhere else: only a release replaces what the running application
 * reads. A save is an upsert keyed by the definition's key, so a client that retries writes the same draft twice.
 * Access is limited to the organization the caller names; the implementation applies the same validators and
 * authorization as the feature's REST endpoints.
 */
public interface DefinitionAuthoringParticipant {

    /** One of {@link DefinitionKinds}' kind constants. */
    String kind();

    /** The JSON Schema of one draft, the contract {@link #saveDraft} accepts. */
    String schema();

    /** The keys of every draft of this kind in the organization. */
    List<String> list(String orgKey);

    /** The draft under {@code key} as JSON in the shape {@link #schema} describes, or empty when there is none. */
    Optional<String> get(String orgKey, String key);

    /**
     * Writes the draft under {@code key}, creating it or replacing it. Returns no issues when it was saved. Otherwise
     * nothing was written, and the issues say what to fix: parse errors, and validation errors of the feature.
     *
     * @throws IllegalArgumentException when the JSON's own key differs from {@code key}, which is a caller bug rather
     *                                  than a definition problem
     */
    List<AuthoringIssue> saveDraft(String orgKey, String key, String json);

    /** Validates the stored draft under {@code key} and returns its issues; empty when it is valid or absent. */
    List<AuthoringIssue> validate(String orgKey, String key);
}
