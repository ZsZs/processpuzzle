package com.processpuzzle.core.definition;

import java.util.List;

/** A release that did not happen, with what refused it. Nothing was released. */
public class OrganizationReleaseRefusedException extends RuntimeException {

    private final transient List<ReleaseProblem> problems;

    public OrganizationReleaseRefusedException(String message, List<ReleaseProblem> problems) {
        super(message);
        this.problems = problems == null ? List.of() : List.copyOf(problems);
    }

    public List<ReleaseProblem> problems() {
        return problems;
    }
}
