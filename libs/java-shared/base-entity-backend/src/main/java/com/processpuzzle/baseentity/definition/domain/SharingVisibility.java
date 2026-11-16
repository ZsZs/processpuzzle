package com.processpuzzle.baseentity.definition.domain;

/** Who may read the records of a shared entity type. */
public enum SharingVisibility {
    /** Every member of the common. */
    PUBLIC,
    /** Only the owning member. Not supported yet. */
    OWNER_ONLY
}
