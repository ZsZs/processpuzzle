package com.processpuzzle.app.usecase.exception;

/**
 * An upload of module translations was refused as a whole: not a readable archive or JSON file, a value
 * that is not text, an entry outside the archive root, or a language that is not supported. Surfaced as 400.
 */
public class ModuleTranslationRejectedException extends RuntimeException {

    public ModuleTranslationRejectedException(String message) {
        super(message);
    }
}
