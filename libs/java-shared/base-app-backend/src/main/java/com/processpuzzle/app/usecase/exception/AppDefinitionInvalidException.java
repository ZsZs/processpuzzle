package com.processpuzzle.app.usecase.exception;

import com.processpuzzle.app.usecase.AppValidationProblem;

import java.util.List;

/**
 * A write was rejected because the definition failed referential-integrity validation. Surfaced as
 * 400. Carries the problems so the designer gets the same detail from a rejected save as it does
 * from the explicit validate endpoint.
 */
public class AppDefinitionInvalidException extends RuntimeException {

    private final List<AppValidationProblem> problems;

    public AppDefinitionInvalidException(String orgKey, String appId, List<AppValidationProblem> problems) {
        super("App definition is invalid: " + orgKey + "/" + appId
                + " (" + (problems == null ? 0 : problems.size()) + " problem(s))");
        this.problems = problems == null ? List.of() : List.copyOf(problems);
    }

    public List<AppValidationProblem> getProblems() {
        return problems;
    }
}
