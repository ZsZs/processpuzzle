package com.processpuzzle.app.usecase;

import com.processpuzzle.rule.domain.Severity;

import java.io.Serializable;
import java.util.List;

/**
 * One thing wrong with a candidate app definition.
 *
 * <p>{@link Severity} is Base Rule's own enum rather than a copy: a problem may originate from a
 * {@code RuleDefinition}, whose severity is carried through verbatim, and re-declaring the scale here
 * would mean mapping between two identical enums for no gain. It is the single type Base Rule exposes
 * as its {@code domain} named interface, precisely so that a caller can read an evaluation result.
 *
 * @param path JSON-pointer-like location of the offending node, e.g. {@code /regions/0/navItems/1/pageId}
 * @param errorId stable, machine-readable identifier, usable as a Transloco key by the designer
 * @param errorText human-readable message in the service's default language
 * @param severity whether this rejects the write or is merely advice — see {@link #blocksPersisting()}
 */
public record AppValidationProblem(String path, String errorId, String errorText, Severity severity)
        implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * A blocking problem. Every structural problem uses this: referential integrity is not a matter
     * of degree, so only rule-derived problems ever carry another severity.
     */
    public AppValidationProblem(String path, String errorId, String errorText) {
        this(path, errorId, errorText, Severity.ERROR);
    }

    /** Whether this problem must reject the write rather than merely be reported to the designer. */
    public boolean blocksPersisting() {
        return severity == Severity.ERROR;
    }

    /**
     * The subset that rejects a write. A definition with no blocking problem is persisted even though
     * it may still carry warnings — that is the state a half-finished draft is legitimately in.
     */
    public static List<AppValidationProblem> blocking(List<AppValidationProblem> problems) {
        return problems == null
                ? List.of()
                : problems.stream().filter(AppValidationProblem::blocksPersisting).toList();
    }
}
