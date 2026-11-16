package com.processpuzzle.starter.bundle;

/** The reserved organization key of a starter's common: {@code <starterId>-common}, one per starter. */
public final class CommonKeys {

    public static final String SUFFIX = "-common";

    private CommonKeys() {
    }

    public static String commonOf(String starterId) {
        return starterId + SUFFIX;
    }

    public static boolean isCommonId(String id) {
        return id != null && id.length() > SUFFIX.length() && id.endsWith(SUFFIX);
    }

    /** The starter a common belongs to; {@code commonId} must satisfy {@link #isCommonId}. */
    public static String starterOf(String commonId) {
        return commonId.substring(0, commonId.length() - SUFFIX.length());
    }
}
