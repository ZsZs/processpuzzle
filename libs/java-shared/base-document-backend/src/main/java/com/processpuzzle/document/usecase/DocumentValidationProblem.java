package com.processpuzzle.document.usecase;

import com.processpuzzle.rule.domain.Severity;

import java.io.Serializable;
import java.util.List;

/**
 * One thing wrong with a candidate document. {@link Severity} is base-rule's own enum, reused
 * directly rather than redeclared here — same precedent {@code AppValidationProblem} sets in
 * base-app-backend, so this module adds a real dependency on base-rule-backend's {@code domain}
 * package rather than a copy. (The OpenAPI contract still locally duplicates the {@code Severity}
 * *schema* for the Windows swagger-parser reason noted there — that is a contract-generation
 * constraint, unrelated to this Java-level dependency choice.)
 *
 * @param path      JSON-pointer-like location, e.g. {@code /blocks/3/props/childIds/0}
 * @param errorId   stable, machine-readable identifier
 * @param errorText human-readable message
 * @param severity  ERROR blocks the write; WARNING (e.g. an orphaned REFERENCED widget) does not
 */
public record DocumentValidationProblem(String path, String errorId, String errorText, Severity severity)
        implements Serializable {

    private static final long serialVersionUID = 1L;

    public DocumentValidationProblem(String path, String errorId, String errorText) {
        this(path, errorId, errorText, Severity.ERROR);
    }

    public boolean blocksPersisting() {
        return severity == Severity.ERROR;
    }

    public static List<DocumentValidationProblem> blocking(List<DocumentValidationProblem> problems) {
        return problems == null
                ? List.of()
                : problems.stream().filter(DocumentValidationProblem::blocksPersisting).toList();
    }
}
