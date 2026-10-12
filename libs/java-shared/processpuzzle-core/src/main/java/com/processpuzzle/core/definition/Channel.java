package com.processpuzzle.core.definition;

/**
 * Which of a definition's two rows a reader means. A designer edits the {@link #DRAFT}; the running
 * application reads the {@link #RELEASED} one, which only a release of the organization replaces.
 */
public enum Channel {
    DRAFT,
    RELEASED;

    /**
     * The channel a {@code revision} query parameter names, {@code draft} when it names none.
     *
     * @throws IllegalArgumentException for anything other than {@code draft} or {@code released}
     */
    public static Channel ofRevision(String revision) {
        if (revision == null || revision.isBlank() || "draft".equalsIgnoreCase(revision)) {
            return DRAFT;
        }
        if ("released".equalsIgnoreCase(revision)) {
            return RELEASED;
        }
        throw new IllegalArgumentException("Unknown revision '" + revision + "'; expected 'draft' or 'released'.");
    }
}
