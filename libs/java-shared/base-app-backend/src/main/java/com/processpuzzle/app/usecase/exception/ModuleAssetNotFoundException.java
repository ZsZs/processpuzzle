package com.processpuzzle.app.usecase.exception;

/** The module has no asset at that path in that channel. Surfaced as 404. */
public class ModuleAssetNotFoundException extends RuntimeException {

    public ModuleAssetNotFoundException(String orgKey, String moduleKey, String path) {
        super("Module asset not found: " + orgKey + "/" + moduleKey + "/" + path);
    }
}
