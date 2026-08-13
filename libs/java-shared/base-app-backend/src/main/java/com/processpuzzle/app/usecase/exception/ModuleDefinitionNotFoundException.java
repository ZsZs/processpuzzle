package com.processpuzzle.app.usecase.exception;

/** No module with this key exists in this organization. Surfaced as 404. */
public class ModuleDefinitionNotFoundException extends RuntimeException {

    public ModuleDefinitionNotFoundException(String orgKey, String moduleKey) {
        super("Module definition not found: " + orgKey + "/" + moduleKey);
    }
}
