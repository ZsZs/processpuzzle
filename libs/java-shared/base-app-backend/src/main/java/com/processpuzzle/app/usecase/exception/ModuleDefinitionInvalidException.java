package com.processpuzzle.app.usecase.exception;

import com.processpuzzle.app.usecase.AppValidationProblem;

import java.util.List;

/**
 * A module write was rejected because the definition failed referential-integrity validation.
 * Surfaced as 400, carrying the problems for the same reason
 * {@link AppDefinitionInvalidException} does.
 */
public class ModuleDefinitionInvalidException extends RuntimeException {

    private final List<AppValidationProblem> problems;

    public ModuleDefinitionInvalidException(String orgKey, String moduleKey,
                                            List<AppValidationProblem> problems) {
        super("Module definition is invalid: " + orgKey + "/" + moduleKey
                + " (" + (problems == null ? 0 : problems.size()) + " problem(s))");
        this.problems = problems == null ? List.of() : List.copyOf(problems);
    }

    public List<AppValidationProblem> getProblems() {
        return problems;
    }
}
