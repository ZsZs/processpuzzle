package com.processpuzzle.app.usecase.exception;

/** A module with this key already exists in this organization. Surfaced as 409. */
public class ModuleDefinitionAlreadyExistsException extends RuntimeException {

    public ModuleDefinitionAlreadyExistsException(String orgKey, String moduleKey) {
        super("Module definition already exists: " + orgKey + "/" + moduleKey);
    }
}
