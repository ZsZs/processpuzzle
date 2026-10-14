package com.processpuzzle.release.usecase;

import com.processpuzzle.core.definition.PendingChange;
import com.processpuzzle.core.definition.ReleaseProblem;
import java.util.List;

/**
 * What a release of an organization would carry, and what would refuse or qualify it.
 *
 * @param changes  every pending definition, in release order
 * @param problems errors refuse the release; warnings let it through once confirmed. Found on the data as the
 *                 pending migration steps leave it
 * @param steps    what each pending migration step did, in order
 */
public record PendingRelease(List<PendingChange> changes, List<ReleaseProblem> problems, List<StepOutcome> steps) {

    public PendingRelease {
        changes = List.copyOf(changes);
        problems = List.copyOf(problems);
        steps = List.copyOf(steps);
    }

    public PendingRelease(List<PendingChange> changes, List<ReleaseProblem> problems) {
        this(changes, problems, List.of());
    }

    public boolean hasErrors() {
        return problems.stream().anyMatch(ReleaseProblem::isError);
    }

    public boolean hasWarnings() {
        return problems.stream().anyMatch(problem -> !problem.isError());
    }
}
