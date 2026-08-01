package com.processpuzzle.app.usecase.exception;

/** No app definition with this id exists in this organization. Surfaced as 404. */
public class AppDefinitionNotFoundException extends RuntimeException {

    public AppDefinitionNotFoundException(String orgKey, String appId) {
        super("App definition not found: " + orgKey + "/" + appId);
    }
}
