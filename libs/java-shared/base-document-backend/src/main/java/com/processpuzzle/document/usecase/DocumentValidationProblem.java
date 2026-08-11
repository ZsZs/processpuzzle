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
 * @param path      JSON-pointer-like location <em>within one translation's content</em>, e.g.
 *                  {@code /blocks/3/props/childIds/0}. Relative rather than absolute because the
 *                  checker validates one locale's content at a time and has no notion of where
 *                  that content sits in a request body; {@code locale} says which one it was.
 * @param errorId   stable, machine-readable identifier
 * @param errorText human-readable message
 * @param severity  ERROR blocks the write; WARNING (e.g. an orphaned REFERENCED widget) does not
 * @param locale    the translation the problem was found in, or {@code null} for a problem about
 *                  the document as a whole
 */
public record DocumentValidationProblem(String path, String errorId, String errorText, Severity severity, String locale)
        implements Serializable {

    private static final long serialVersionUID = 1L;

    public DocumentValidationProblem(String path, String errorId, String errorText) {
        this(path, errorId, errorText, Severity.ERROR, null);
    }

    public DocumentValidationProblem(String path, String errorId, String errorText, Severity severity) {
        this(path, errorId, errorText, severity, null);
    }

    /**
     * Stamps the locale onto a problem the checker produced. The checker is deliberately
     * locale-agnostic — it validates a block list against a port set and neither knows nor needs
     * to know which language it was handed — so attributing the finding is the caller's job.
     */
    public DocumentValidationProblem withLocale(String theLocale) {
        return new DocumentValidationProblem(path, errorId, errorText, severity, theLocale);
    }

    public boolean blocksPersisting() {
        return severity == Severity.ERROR;
    }

    public static List<DocumentValidationProblem> blocking(List<DocumentValidationProblem> problems) {
        return problems == null
                ? List.of()
                : problems.stream().filter(DocumentValidationProblem::blocksPersisting).toList();
    }

    public static List<DocumentValidationProblem> withLocale(List<DocumentValidationProblem> problems, String theLocale) {
        return problems == null
                ? List.of()
                : problems.stream().map(problem -> problem.withLocale(theLocale)).toList();
    }
}
