package com.processpuzzle.app.usecase.exception;

/** The app has no asset at that path in that channel. Surfaced as 404. */
public class AppAssetNotFoundException extends RuntimeException {

    public AppAssetNotFoundException(String orgKey, String appId, String path) {
        super("App asset not found: " + orgKey + "/" + appId + "/" + path);
    }
}
