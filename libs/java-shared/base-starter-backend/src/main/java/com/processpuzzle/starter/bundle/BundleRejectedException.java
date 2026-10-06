package com.processpuzzle.starter.bundle;

/** A bundle that cannot be read as a starter at all — before any of its definitions is looked at. */
public class BundleRejectedException extends RuntimeException {

    public enum Reason {
        /** Malformed, unsafe or inconsistent with its own manifest. */
        INVALID,
        /** Over one of the {@code base-starter.bundle} limits. */
        TOO_LARGE
    }

    private final Reason reason;
    private final String file;

    public BundleRejectedException(Reason reason, String file, String message) {
        super(message);
        this.reason = reason;
        this.file = file;
    }

    public static BundleRejectedException invalid(String file, String message) {
        return new BundleRejectedException(Reason.INVALID, file, message);
    }

    public static BundleRejectedException tooLarge(String message) {
        return new BundleRejectedException(Reason.TOO_LARGE, null, message);
    }

    public Reason getReason() {
        return reason;
    }

    /** The offending path inside the bundle, or null when the problem is not one file's. */
    public String getFile() {
        return file;
    }
}
