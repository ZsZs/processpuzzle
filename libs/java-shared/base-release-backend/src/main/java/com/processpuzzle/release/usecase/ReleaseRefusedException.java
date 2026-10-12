package com.processpuzzle.release.usecase;

/** A release that did not happen, and the pending state that says why. Nothing was released. */
public class ReleaseRefusedException extends RuntimeException {

    public enum Reason {
        NOTHING_PENDING, ERRORS, UNCONFIRMED_WARNINGS
    }

    private final transient PendingRelease pending;
    private final Reason reason;

    public ReleaseRefusedException(String orgKey, PendingRelease pending, Reason reason) {
        super("Organization '" + orgKey + "' " + switch (reason) {
            case NOTHING_PENDING -> "has nothing to release.";
            case ERRORS -> "cannot be released: its drafts have errors.";
            case UNCONFIRMED_WARNINGS -> "has release warnings that were not confirmed.";
        });
        this.pending = pending;
        this.reason = reason;
    }

    public PendingRelease getPending() {
        return pending;
    }

    public Reason getReason() {
        return reason;
    }
}
