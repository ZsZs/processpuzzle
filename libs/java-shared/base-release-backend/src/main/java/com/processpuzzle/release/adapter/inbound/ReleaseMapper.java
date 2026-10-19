package com.processpuzzle.release.adapter.inbound;

import com.processpuzzle.core.definition.MigrationStep;
import com.processpuzzle.core.definition.PendingChange;
import com.processpuzzle.core.definition.ProblemResolution;
import com.processpuzzle.core.definition.ReleaseProblem;
import com.processpuzzle.release.domain.OrgMigrationStep;
import com.processpuzzle.release.domain.OrgReleaseItem;
import com.processpuzzle.release.model.DefinitionChange;
import com.processpuzzle.release.model.MigrationStepOutcome;
import com.processpuzzle.release.model.PendingRelease;
import com.processpuzzle.release.model.Release;
import com.processpuzzle.release.model.ReleaseItem;
import com.processpuzzle.release.usecase.ReleaseRecord;
import com.processpuzzle.release.usecase.StepOutcome;
import java.util.LinkedHashMap;
import java.util.List;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

/** Between the use cases' types and base-release-api.yaml's. */
@Component
public class ReleaseMapper {

    public PendingRelease toModel(com.processpuzzle.release.usecase.PendingRelease pending) {
        PendingRelease model = new PendingRelease(
                pending.changes().stream().map(this::toModel).toList(),
                pending.problems().stream().map(this::toModel).toList());
        model.setSteps(pending.steps().stream().map(this::toModel).toList());
        return model;
    }

    public com.processpuzzle.release.model.MigrationStep toModel(MigrationStep step) {
        com.processpuzzle.release.model.MigrationStep model =
                new com.processpuzzle.release.model.MigrationStep(step.kind(), step.key(), step.operation());
        model.setId(step.id());
        model.setSubject(step.subject());
        model.setParameters(new LinkedHashMap<>(step.parameters()));
        return model;
    }

    public MigrationStep fromModel(com.processpuzzle.release.model.MigrationStep step) {
        return new MigrationStep(null, step.getKind(), step.getKey(), step.getOperation(), step.getSubject(),
                step.getParameters());
    }

    public List<MigrationStep> fromModel(List<com.processpuzzle.release.model.MigrationStep> steps) {
        return steps == null ? List.of() : steps.stream().map(this::fromModel).toList();
    }

    private MigrationStepOutcome toModel(StepOutcome outcome) {
        MigrationStepOutcome model = new MigrationStepOutcome(toModel(outcome.step()), switch (outcome.status()) {
            case APPLIED -> MigrationStepOutcome.OutcomeEnum.APPLIED;
            case SKIPPED -> MigrationStepOutcome.OutcomeEnum.SKIPPED;
            case FAILED -> MigrationStepOutcome.OutcomeEnum.FAILED;
        });
        model.setAffectedObjects(outcome.affectedObjects());
        model.setReason(outcome.reason());
        return model;
    }

    private MigrationStepOutcome toModel(OrgMigrationStep step) {
        MigrationStepOutcome model = new MigrationStepOutcome(toModel(step.toStep()),
                step.getOutcome() == OrgMigrationStep.Outcome.APPLIED
                        ? MigrationStepOutcome.OutcomeEnum.APPLIED
                        : MigrationStepOutcome.OutcomeEnum.SKIPPED);
        model.setAffectedObjects(step.getAffectedObjects());
        model.setReason(step.getReason());
        return model;
    }

    public Release toModel(ReleaseRecord releaseRecord) {
        Release release = new Release(
                releaseRecord.release().getReleaseNumber(),
                releaseRecord.release().getReleasedAt().atOffset(ZoneOffset.UTC),
                releaseRecord.items().stream().map(this::toModel).toList());
        release.setReleasedBy(releaseRecord.release().getReleasedBy());
        release.setSteps(releaseRecord.steps().stream().map(this::toModel).toList());
        return release;
    }

    private com.processpuzzle.release.model.PendingChange toModel(PendingChange change) {
        return new com.processpuzzle.release.model.PendingChange(change.kind(), change.key(), toModel(change.change()));
    }

    private com.processpuzzle.release.model.ReleaseProblem toModel(ReleaseProblem problem) {
        com.processpuzzle.release.model.ReleaseProblem model = new com.processpuzzle.release.model.ReleaseProblem(
                problem.isError()
                        ? com.processpuzzle.release.model.ReleaseProblem.SeverityEnum.ERROR
                        : com.processpuzzle.release.model.ReleaseProblem.SeverityEnum.WARNING,
                problem.kind(), problem.key(), problem.errorId(), problem.message());
        model.setSubject(problem.subject());
        model.setAffectedObjects(problem.affectedObjects());
        model.setResolutions(problem.resolutions().stream().map(this::toModel).toList());
        return model;
    }

    private com.processpuzzle.release.model.ProblemResolution toModel(ProblemResolution resolution) {
        com.processpuzzle.release.model.ProblemResolution model =
                new com.processpuzzle.release.model.ProblemResolution(toModel(resolution.step()));
        model.setInput(resolution.input());
        model.setInputKind(resolution.inputKind() == null ? null
                : com.processpuzzle.release.model.ProblemResolution.InputKindEnum.fromValue(resolution.inputKind()));
        model.setChoices(resolution.choices());
        return model;
    }

    private ReleaseItem toModel(OrgReleaseItem item) {
        ReleaseItem model = new ReleaseItem(item.getKind(), item.getDefinitionKey(), toModel(item.getChange()));
        model.setContentHash(item.getContentHash());
        return model;
    }

    private static DefinitionChange toModel(PendingChange.Change change) {
        return switch (change) {
            case ADDED -> DefinitionChange.ADDED;
            case CHANGED -> DefinitionChange.CHANGED;
            case REMOVED -> DefinitionChange.REMOVED;
        };
    }
}
