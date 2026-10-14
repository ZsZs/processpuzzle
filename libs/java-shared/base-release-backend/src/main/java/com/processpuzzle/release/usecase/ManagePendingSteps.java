package com.processpuzzle.release.usecase;

import com.processpuzzle.core.definition.MigrationStep;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import com.processpuzzle.release.domain.OrgMigrationStep;
import com.processpuzzle.release.domain.OrgMigrationStepRepository;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The migration steps an organization's next release runs. A designer replaces them as a whole, in the order they are
 * to run; each gets an id when it is stored. A step whose kind or operation nothing runs is refused here, whereas a
 * step that no longer fits the drafts is kept and reported as skipped, so the designer sees it.
 */
@Service
public class ManagePendingSteps {

    private final OrganizationGuard guard;
    private final MigrationParticipants migrations;
    private final OrgMigrationStepRepository stepRepository;
    private final TransactionTemplate transactionTemplate;

    ManagePendingSteps(OrganizationGuard guard, MigrationParticipants migrations, OrgMigrationStepRepository stepRepository,
                       PlatformTransactionManager transactionManager) {
        this.guard = guard;
        this.migrations = migrations;
        this.stepRepository = stepRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public List<MigrationStep> list(String orgKey) {
        guard.requireDesign(orgKey);
        return pending(orgKey);
    }

    /**
     * @throws UnknownMigrationStepException if a step names a kind or an operation nothing runs; nothing is replaced
     */
    public List<MigrationStep> replace(String orgKey, List<MigrationStep> steps) {
        guard.requireDesign(orgKey);
        for (MigrationStep step : steps) {
            if (migrations.running(step.kind(), step.operation()).isEmpty()) {
                throw new UnknownMigrationStepException(step);
            }
        }
        return transactionTemplate.execute(status -> {
            stepRepository.deletePending(orgKey);
            stepRepository.saveAll(IntStream.range(0, steps.size())
                    .mapToObj(index -> new OrgMigrationStep(orgKey, index, steps.get(index)))
                    .toList());
            return pending(orgKey);
        });
    }

    private List<MigrationStep> pending(String orgKey) {
        return stepRepository.findByOrgKeyAndReleaseNumberIsNullOrderBySequenceNoAsc(orgKey).stream()
                .map(OrgMigrationStep::toStep)
                .toList();
    }

    /** A step no migration participant runs. */
    public static class UnknownMigrationStepException extends RuntimeException {

        public UnknownMigrationStepException(MigrationStep step) {
            super("Nothing runs the migration operation '%s' on a %s.".formatted(step.operation(), step.kind()));
        }
    }
}
