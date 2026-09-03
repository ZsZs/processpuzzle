package com.processpuzzle.platformadmin.domain;

/**
 * Lifecycle of a subscription. Persisted as a string.
 *
 * <p>Deliberately independent of {@link OrganizationStatus}: a tenant can be {@code ACTIVE} while
 * its subscription is {@code PAST_DUE} — that is the normal grace period, not a contradiction — and
 * a {@code SUSPENDED} tenant keeps whatever subscription state it had, because suspension retains
 * data and is reversible. Collapsing the two into one field would make "stop billing" and "revoke
 * access" the same operation, which they are not.
 */
public enum SubscriptionStatus {

    /** Before the first invoice. */
    TRIALING,

    /** Paid and current. */
    ACTIVE,

    /** An invoice is overdue but service continues — the grace period. */
    PAST_DUE,

    /** Terminated. */
    CANCELED
}
