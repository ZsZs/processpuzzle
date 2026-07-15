package com.processpuzzle.store.usecases.inbound;

public final class ThumbnailNaming {
    public static final String SUFFIX = "-thumb";

    private ThumbnailNaming() {
    }

    public static String thumbnailKey(String objectID) {
        return objectID + SUFFIX;
    }
}
