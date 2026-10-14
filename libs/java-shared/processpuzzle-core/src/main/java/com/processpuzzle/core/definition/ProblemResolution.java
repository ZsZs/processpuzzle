package com.processpuzzle.core.definition;

import java.util.List;

/**
 * A way to resolve a {@link ReleaseProblem}: the {@link MigrationStep} that would, prepared as far as the participant
 * that found the problem can, and the one value the designer still has to give it. A designer applies a resolution by
 * filling in that value and adding the step to the pending ones.
 *
 * @param step      the step, without an id and without the value asked for
 * @param input     the parameter the designer gives a value for — a name such as {@code value}, or a path into a map
 *                  parameter such as {@code mapping.PENDING} — or {@code null} when the step needs nothing more
 * @param inputKind the kind of value {@code input} takes: {@code text}, {@code number}, {@code boolean}, {@code date}
 *                  or {@code dateTime}; {@code null} when there is no input
 * @param choices   the values {@code input} may take; empty when any value of {@code inputKind} will do
 */
public record ProblemResolution(MigrationStep step, String input, String inputKind, List<String> choices) {

    public ProblemResolution {
        choices = choices == null ? List.of() : List.copyOf(choices);
    }

    /** A resolution that asks for one value of {@code inputKind}, from {@code choices} when there are any. */
    public static ProblemResolution asking(MigrationStep step, String input, String inputKind, List<String> choices) {
        return new ProblemResolution(step, input, inputKind, choices);
    }

    /** A resolution the step alone makes, asking for nothing. */
    public static ProblemResolution of(MigrationStep step) {
        return new ProblemResolution(step, null, null, List.of());
    }
}
