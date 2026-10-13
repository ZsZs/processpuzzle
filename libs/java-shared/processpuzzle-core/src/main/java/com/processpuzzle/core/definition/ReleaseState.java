package com.processpuzzle.core.definition;

import java.util.HashMap;
import java.util.Map;

/**
 * Where a draft stands against what its organization released, as the designer's lists show it. Derived from the
 * same comparison a release makes ({@link ReleaseParticipant#pending}), so a list and the Release section cannot
 * disagree.
 *
 * <p>A released definition whose draft was deleted has no row in a designer's list; the Release section shows it as
 * {@link PendingChange.Change#REMOVED}.
 */
public enum ReleaseState {

    /** Never released: the running application does not know it. */
    NEW,
    /** Released, and the draft has changed since. */
    CHANGED,
    /** The draft is what the running application uses. */
    RELEASED;

    /** The state of each of the participant's drafts in the organization, by key. */
    public static Map<String, ReleaseState> ofDrafts(ReleaseParticipant participant, String orgKey) {
        Map<String, ReleaseState> states = new HashMap<>();
        participant.drafts(orgKey).forEach(key -> states.put(key, RELEASED));
        for (PendingChange change : participant.pending(orgKey)) {
            switch (change.change()) {
                case ADDED -> states.put(change.key(), NEW);
                case CHANGED -> states.put(change.key(), CHANGED);
                case REMOVED -> states.remove(change.key());
            }
        }
        return states;
    }
}
