package com.processpuzzle.core.definition;

/**
 * Releases an organization's drafts on its behalf, for server-side code that has written them itself and must make
 * them live in the same transaction: the upgrade of a common. Implemented by base-release; a host without it cannot
 * release a common.
 *
 * <p>Joins the caller's transaction. A refusal is an exception so that the caller's transaction rolls back with it —
 * the drafts it wrote are not left half-way.
 */
public interface OrganizationReleaser {

    /**
     * Releases every pending draft of {@code orgKey}, confirming its warnings: the caller has vetted what it wrote.
     *
     * @return what happened; releasing nothing, because nothing was pending, is not a refusal
     * @throws OrganizationReleaseRefusedException if the release was refused; nothing was released
     */
    Outcome release(String orgKey, String releasedBy);

    enum Outcome {
        RELEASED, NOTHING_PENDING
    }
}
