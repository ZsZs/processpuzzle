package com.processpuzzle.app.usecase;

/**
 * One thing wrong with a candidate app definition.
 *
 * @param path JSON-pointer-like location of the offending node, e.g. {@code /regions/0/navItems/1/pageId}
 * @param errorId stable, machine-readable identifier, usable as a Transloco key by the designer
 * @param errorText human-readable message in the service's default language
 */
public record AppValidationProblem(String path, String errorId, String errorText) {
}
