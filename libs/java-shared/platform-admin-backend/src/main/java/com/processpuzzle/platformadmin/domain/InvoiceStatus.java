package com.processpuzzle.platformadmin.domain;

/** Lifecycle of an invoice. Persisted as a string. */
public enum InvoiceStatus {

    /** Being assembled; has no number yet. */
    DRAFT,

    /** Sent to the customer. */
    ISSUED,

    /** Settled. */
    PAID,

    /** Cancelled after issue. Kept rather than deleted, because the number was already used. */
    VOID
}
