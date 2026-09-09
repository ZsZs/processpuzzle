package com.processpuzzle.document.domain;

/**
 * Publication state of one translation. Derived from the revision counters on every read and
 * never stored, so there is no second source of truth to fall out of step with the content.
 *
 * <p>Note what is absent: a withdrawn state. Unpublishing discards the published snapshot rather
 * than setting a flag, so a withdrawn translation reports {@link #DRAFT} again — the absence of a
 * snapshot <em>is</em> that state, and one fewer stored field is one fewer way for the state and
 * the content to disagree.
 */
public enum DocumentStatus {

    /** Never published, or published and later withdrawn. Not readable by the public path. */
    DRAFT,

    /** The published snapshot is current: nothing has been edited since it was taken. */
    PUBLISHED,

    /** Published, but the draft has moved on — readers still see the older snapshot. */
    PUBLISHED_WITH_DRAFT_CHANGES;

    /**
     * @param revision          the draft's current revision
     * @param publishedRevision the revision currently served, or {@code null} when unpublished
     */
    public static DocumentStatus derive(Long revision, Long publishedRevision) {
        if (publishedRevision == null) {
            return DRAFT;
        }
        return publishedRevision.equals(revision) ? PUBLISHED : PUBLISHED_WITH_DRAFT_CHANGES;
    }
}
