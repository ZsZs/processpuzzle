package com.processpuzzle.core.definition;

/**
 * The organization as it would be after the release: every participant's drafts. Lets a
 * participant's {@link ReleaseParticipant#check} ask about another kind without depending on the
 * feature that owns it.
 */
public interface ReleaseCandidate {

    /** Whether a draft of this kind and key exists, that is, whether the release would carry it. */
    boolean exists(String kind, String key);

    /** Whether some participant releases this kind. A reference into a kind nobody releases is not checked. */
    boolean manages(String kind);
}
