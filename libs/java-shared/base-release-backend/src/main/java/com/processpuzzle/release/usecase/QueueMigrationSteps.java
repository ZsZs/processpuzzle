package com.processpuzzle.release.usecase;

import com.processpuzzle.core.definition.MigrationStep;
import com.processpuzzle.core.definition.MigrationStepQueue;
import com.processpuzzle.release.domain.OrgMigrationStep;
import com.processpuzzle.release.domain.OrgMigrationStepRepository;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;

/**
 * {@link MigrationStepQueue} over the pending steps of {@link ManagePendingSteps}, without a principal's design rights:
 * for code that brings steps with it. The steps go after the ones already queued, in the order given, and a step no
 * migration participant runs is refused as the designer's own would be.
 */
@Component
public class QueueMigrationSteps implements MigrationStepQueue {

    private final MigrationParticipants migrations;
    private final OrgMigrationStepRepository stepRepository;

    QueueMigrationSteps(MigrationParticipants migrations, OrgMigrationStepRepository stepRepository) {
        this.migrations = migrations;
        this.stepRepository = stepRepository;
    }

    @Override
    public void enqueue(String orgKey, List<MigrationStep> steps) {
        for (MigrationStep step : steps) {
            if (migrations.running(step.kind(), step.operation()).isEmpty()) {
                throw new IllegalArgumentException("Nothing runs the migration operation '%s' on a %s."
                    .formatted(step.operation(), step.kind()));
            }
        }
        int first = stepRepository.findByOrgKeyAndReleaseNumberIsNullOrderBySequenceNoAsc(orgKey).size();
        stepRepository.saveAll(IntStream.range(0, steps.size())
            .mapToObj(index -> new OrgMigrationStep(orgKey, first + index, steps.get(index)))
            .toList());
    }
}
