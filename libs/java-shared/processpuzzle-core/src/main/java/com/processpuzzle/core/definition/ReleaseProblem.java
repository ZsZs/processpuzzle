package com.processpuzzle.core.definition;

/**
 * Why a definition should not be released as it stands. An {@link Severity#ERROR} refuses the whole
 * release; a {@link Severity#WARNING} lets it through once the designer has confirmed it.
 *
 * @param kind    the kind of the definition the problem is about
 * @param key     the key of that definition
 * @param errorId stable, machine-readable identifier, also the problem's translation key
 * @param message human-readable explanation in the platform's default language
 */
public record ReleaseProblem(Severity severity, String kind, String key, String errorId, String message) {

    public enum Severity {
        ERROR, WARNING
    }

    public static ReleaseProblem error(String kind, String key, String errorId, String message) {
        return new ReleaseProblem(Severity.ERROR, kind, key, errorId, message);
    }

    public static ReleaseProblem warning(String kind, String key, String errorId, String message) {
        return new ReleaseProblem(Severity.WARNING, kind, key, errorId, message);
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }
}
