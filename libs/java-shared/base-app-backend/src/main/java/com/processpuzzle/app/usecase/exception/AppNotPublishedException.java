package com.processpuzzle.app.usecase.exception;

/**
 * A published revision was requested but this app definition has never been published. Surfaced
 * as 404, which is what the contract declares for the layout and page endpoints.
 */
public class AppNotPublishedException extends RuntimeException {

    public AppNotPublishedException(String orgKey, String appId) {
        super("App definition has no published revision: " + orgKey + "/" + appId);
    }
}
