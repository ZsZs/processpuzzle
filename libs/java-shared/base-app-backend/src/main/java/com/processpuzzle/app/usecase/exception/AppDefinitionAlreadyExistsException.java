package com.processpuzzle.app.usecase.exception;

/** An app definition with this id already exists in this organization. Surfaced as 409. */
public class AppDefinitionAlreadyExistsException extends RuntimeException {

    public AppDefinitionAlreadyExistsException(String orgKey, String appId) {
        super("App definition already exists: " + orgKey + "/" + appId);
    }
}
