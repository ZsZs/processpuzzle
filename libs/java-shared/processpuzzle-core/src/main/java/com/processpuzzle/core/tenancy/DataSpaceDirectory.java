package com.processpuzzle.core.tenancy;

import java.util.Optional;

/**
 * Which organizations share a data space — a <em>common</em>.
 *
 * <p>A common is an ordinary organization, kept by the platform for one business starter: the entity types it
 * shares are defined and released once, there, and every organization that installed the starter is a member who
 * reads them. The directory answers the two questions the features ask, and nothing about what is shared. Membership
 * is derived from what is installed, so there is nothing to keep in step.
 *
 * <p>The module that knows what is installed implements this; a host without one has no commons, which the feature
 * modules take as "nothing is shared". See {@code docs/shared-data-commons-concept}.
 */
public interface DataSpaceDirectory {

    /** The common {@code orgKey} is a member of; empty for an organization without one, and for a common itself. */
    Optional<String> commonOf(String orgKey);

    /** Whether {@code orgKey} is a common, that is, the home of shared definitions and records. */
    boolean isCommon(String orgKey);
}
