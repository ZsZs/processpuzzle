package com.processpuzzle.core.definition;

import java.util.List;

/**
 * Why a definition should not be released as it stands. An {@link Severity#ERROR} refuses the whole
 * release; a {@link Severity#WARNING} lets it through once the designer has confirmed it.
 *
 * @param kind            the kind of the definition the problem is about
 * @param key             the key of that definition
 * @param errorId         stable, machine-readable identifier, also the problem's translation key
 * @param message         human-readable explanation in the platform's default language
 * @param subject         the part of the definition the problem is about — an attribute's code, a state's key —
 *                        or {@code null} when it is about the definition as a whole
 * @param affectedObjects how many existing objects the problem is about, or {@code null} when it is not about data
 * @param resolutions     the migration steps that would resolve the problem; empty when no step can
 */
public record ReleaseProblem(Severity severity, String kind, String key, String errorId, String message,
                             String subject, Long affectedObjects, List<ProblemResolution> resolutions) {

    public enum Severity {
        ERROR, WARNING
    }

    public ReleaseProblem {
        resolutions = resolutions == null ? List.of() : List.copyOf(resolutions);
    }

    public ReleaseProblem(Severity severity, String kind, String key, String errorId, String message) {
        this(severity, kind, key, errorId, message, null, null, List.of());
    }

    public static ReleaseProblem error(String kind, String key, String errorId, String message) {
        return new ReleaseProblem(Severity.ERROR, kind, key, errorId, message);
    }

    public static ReleaseProblem warning(String kind, String key, String errorId, String message) {
        return new ReleaseProblem(Severity.WARNING, kind, key, errorId, message);
    }

    /** The same problem, about {@code subject} of the definition and the {@code affectedObjects} it concerns. */
    public ReleaseProblem about(String subject, Long affectedObjects) {
        return new ReleaseProblem(severity, kind, key, errorId, message, subject, affectedObjects, resolutions);
    }

    /** The same problem, resolvable by any of {@code resolutions}. */
    public ReleaseProblem resolvableBy(List<ProblemResolution> resolutions) {
        return new ReleaseProblem(severity, kind, key, errorId, message, subject, affectedObjects, resolutions);
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }
}
