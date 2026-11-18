package com.processpuzzle.baseentity.definition.domain;

/** Who may write the records of a shared entity type. */
public enum SharingWrite {
    /** Only the common's own members, the platform staff who curate it. */
    COMMON_ADMIN,
    /** The member that created the record, and the common's own members. */
    OWNER_ORG,
    /** The person the record describes. Reserved. */
    SELF
}
