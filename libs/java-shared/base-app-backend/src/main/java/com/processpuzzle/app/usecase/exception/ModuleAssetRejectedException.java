package com.processpuzzle.app.usecase.exception;

/** An upload of a module asset was refused: an invalid path, a file over the size limit, or too many assets for the module. Surfaced as 400. */
public class ModuleAssetRejectedException extends RuntimeException {

    public ModuleAssetRejectedException(String message) {
        super(message);
    }
}
