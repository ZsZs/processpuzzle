package com.processpuzzle.release.usecase;

import com.processpuzzle.core.definition.MigrationParticipant;
import com.processpuzzle.core.definition.MigrationStep;
import com.processpuzzle.core.definition.MigrationStepFailedException;
import com.processpuzzle.core.definition.ReleaseProblem;
import com.processpuzzle.release.domain.OrgMigrationStep;
import com.processpuzzle.release.domain.OrgMigrationStepRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Plans a release on the data as the organization's pending {@link MigrationStep}s leave it: runs every step, in
 * order, then asks the {@link ReleasePlanner}. The checks therefore judge the objects as they would be released, and a
 * step that resolves a problem makes it disappear.
 *
 * <p>Writes. The caller owns the transaction, and either commits it — the release — or rolls it back — the dry run.
 */
@Component
class MigratingPlanner {

    private final ReleasePlanner planner;
    private final MigrationParticipants migrations;
    private final OrgMigrationStepRepository stepRepository;

    MigratingPlanner(ReleasePlanner planner, MigrationParticipants migrations, OrgMigrationStepRepository stepRepository) {
        this.planner = planner;
        this.migrations = migrations;
        this.stepRepository = stepRepository;
    }

    /** The pending release, and the pending steps it ran, in the order of its outcomes. */
    record Planned(PendingRelease pending, List<OrgMigrationStep> steps) {
    }

    Planned plan(String orgKey) {
        List<OrgMigrationStep> steps = stepRepository.findByOrgKeyAndReleaseNumberIsNullOrderBySequenceNoAsc(orgKey);
        List<StepOutcome> outcomes = steps.stream().map(step -> run(orgKey, step.toStep())).toList();
        PendingRelease planned = planner.plan(orgKey);
        List<ReleaseProblem> problems = new ArrayList<>(planned.problems());
        outcomes.stream().map(StepOutcome::problem).flatMap(Optional::stream).forEach(problems::add);
        return new Planned(new PendingRelease(planned.changes(), problems, outcomes), steps);
    }

    private StepOutcome run(String orgKey, MigrationStep step) {
        Optional<MigrationParticipant> participant = migrations.running(step.kind(), step.operation());
        if (participant.isEmpty()) {
            return StepOutcome.skipped(step, "nothing runs '%s' on a %s.".formatted(step.operation(), step.kind()));
        }
        Optional<String> unusable = participant.get().unusable(orgKey, step);
        if (unusable.isPresent()) {
            return StepOutcome.skipped(step, unusable.get());
        }
        try {
            return StepOutcome.applied(step, participant.get().apply(orgKey, step));
        } catch (MigrationStepFailedException e) {
            return StepOutcome.failed(step, e.getMessage());
        }
    }
}
