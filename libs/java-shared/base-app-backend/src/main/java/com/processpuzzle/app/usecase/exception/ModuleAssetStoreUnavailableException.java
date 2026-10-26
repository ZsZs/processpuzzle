package com.processpuzzle.app.usecase.exception;

/** No object store is wired behind the module assets, so their content can be neither stored nor read. Surfaced as 503. */
public class ModuleAssetStoreUnavailableException extends RuntimeException {

    public ModuleAssetStoreUnavailableException(String message) {
        super(message);
    }
}
