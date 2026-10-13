package com.processpuzzle.core.definition;

import java.util.List;
import java.util.Set;

/**
 * Releases the definitions of one kind: makes an organization's drafts what its running application
 * reads.
 *
 * <p>Implemented by each feature library for its own definitions, and collected by
 * {@code base-release-backend}, which runs every participant inside one transaction it owns, in
 * {@link #order()}. A designer edits drafts only. A release first asks every participant what it would
 * change ({@link #pending}), what its drafts refer to ({@link #references}) and what would object
 * ({@link #check}), and refuses the whole release on any error. Only then is {@link #release} called,
 * for every participant.
 *
 * <p>The obligations of {@link DefinitionImportParticipant} hold here too: {@link #release} writes
 * through the transaction it is called in and does not commit it, and a side effect outside the
 * database waits for the commit.
 */
public interface ReleaseParticipant {

    /** One of {@link DefinitionKinds}' kind constants. */
    String kind();

    /** Where this kind is released relative to the others; see {@link DefinitionKinds}' order constants. */
    int order();

    /** The keys of every draft of this kind, changed or not: what the organization holds once released. */
    Set<String> drafts(String orgKey);

    /**
     * Every key another definition may refer to one of this kind's drafts by — what a reference into this kind is
     * resolved against. Its {@linkplain #drafts keys} unless the kind is also known by another name: an entity type,
     * for one, is keyed by its code but named by its name in app routes, state machines and links.
     */
    default Set<String> referableKeys(String orgKey) {
        return drafts(orgKey);
    }

    /** Every definition of this kind whose draft differs from its released row. */
    List<PendingChange> pending(String orgKey);

    /**
     * What this kind's drafts refer to. Also the answer to "who refers to this definition?", which a
     * guard against deleting a definition in use asks.
     */
    List<DefinitionReference> references(String orgKey);

    /**
     * What would break if this kind's drafts were released: a draft that fails its own validation, or
     * live data that a changed definition no longer describes. References are checked by the caller.
     */
    List<ReleaseProblem> check(String orgKey, ReleaseCandidate candidate);

    /** Makes every draft of this kind the released row, and deletes the released rows whose draft is gone. */
    void release(String orgKey);
}
