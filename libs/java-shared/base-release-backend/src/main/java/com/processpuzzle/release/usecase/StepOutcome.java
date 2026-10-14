package com.processpuzzle.release.usecase;

import com.processpuzzle.core.definition.MigrationStep;
import com.processpuzzle.core.definition.ReleaseProblem;
import java.util.Optional;

/**
 * What running one {@link MigrationStep} did.
 *
 * @param affectedObjects how many objects it changed; null unless it was applied
 * @param reason          why it was skipped or failed; null when it was applied
 */
public record StepOutcome(MigrationStep step, Status status, Long affectedObjects, String reason) {

    public enum Status {
        /** The step ran. */
        APPLIED,
        /** The step cannot run against the definitions as they stand, and did not. */
        SKIPPED,
        /** Some object could not be changed as the step says; refuses the release. */
        FAILED
    }

    static final String SKIPPED_STEP = "release.step.skipped";
    static final String FAILED_STEP = "release.step.failed";

    static StepOutcome applied(MigrationStep step, long affectedObjects) {
        return new StepOutcome(step, Status.APPLIED, affectedObjects, null);
    }

    static StepOutcome skipped(MigrationStep step, String reason) {
        return new StepOutcome(step, Status.SKIPPED, null, reason);
    }

    static StepOutcome failed(MigrationStep step, String reason) {
        return new StepOutcome(step, Status.FAILED, null, reason);
    }

    /** A skipped step is a warning — what it was to resolve stays unresolved — and a failed one an error. */
    Optional<ReleaseProblem> problem() {
        return switch (status) {
            case APPLIED -> Optional.empty();
            case SKIPPED -> Optional.of(ReleaseProblem.warning(step.kind(), step.key(), SKIPPED_STEP,
                    "A migration step is skipped: " + reason).about(step.subject(), null));
            case FAILED -> Optional.of(ReleaseProblem.error(step.kind(), step.key(), FAILED_STEP,
                    "A migration step failed: " + reason).about(step.subject(), null));
        };
    }
}
