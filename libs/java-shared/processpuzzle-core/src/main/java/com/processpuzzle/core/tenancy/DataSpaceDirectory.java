package com.processpuzzle.core.tenancy;

import java.util.Set;

/**
 * Which organizations share a data space — a <em>common</em>.
 *
 * <p>A common is an ordinary organization, kept by the platform for one business starter: the entity types it
 * shares are defined and released once, there, and every organization that installed the starter is a member who
 * reads them. An organization may install several starters, so it may be a member of several commons — one for each
 * starter that has one — and sees the shared types of all of them. The directory answers the two questions the features
 * ask, and nothing about what is shared. Membership is derived from what is installed, so there is nothing to keep in
 * step.
 *
 * <p>The module that knows what is installed implements this; a host without one has no commons, which the feature
 * modules take as "nothing is shared". See {@code docs/shared-data-commons-concept}.
 */
public interface DataSpaceDirectory {

    /**
     * The commons {@code orgKey} is a member of — one for each installed starter that has one — in a stable order (by
     * key); empty for an organization without one, and for a common itself.
     *
     * <p>A shared type belongs to the common of the starter that defines it, and the keys of two starters never meet
     * (every key starts with its starter's prefix), so a code is shared by at most one of them.
     */
    Set<String> commonsOf(String orgKey);

    /** Whether {@code orgKey} is a common, that is, the home of shared definitions and records. */
    boolean isCommon(String orgKey);

    /** The organizations that are members of the common {@code commonKey}: those that installed its starter. Empty for any other key. */
    default Set<String> membersOf(String commonKey) {
        return Set.of();
    }
}
