package com.processpuzzle.release.adapter.inbound;

import com.processpuzzle.core.definition.PendingChange;
import com.processpuzzle.core.definition.ReleaseProblem;
import com.processpuzzle.release.domain.OrgReleaseItem;
import com.processpuzzle.release.model.DefinitionChange;
import com.processpuzzle.release.model.PendingRelease;
import com.processpuzzle.release.model.Release;
import com.processpuzzle.release.model.ReleaseItem;
import com.processpuzzle.release.usecase.ReleaseRecord;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

/** Between the use cases' types and base-release-api.yaml's. */
@Component
public class ReleaseMapper {

    public PendingRelease toModel(com.processpuzzle.release.usecase.PendingRelease pending) {
        return new PendingRelease(
                pending.changes().stream().map(this::toModel).toList(),
                pending.problems().stream().map(this::toModel).toList());
    }

    public Release toModel(ReleaseRecord record) {
        Release release = new Release(
                record.release().getReleaseNumber(),
                record.release().getReleasedAt().atOffset(ZoneOffset.UTC),
                record.items().stream().map(this::toModel).toList());
        release.setReleasedBy(record.release().getReleasedBy());
        return release;
    }

    private com.processpuzzle.release.model.PendingChange toModel(PendingChange change) {
        return new com.processpuzzle.release.model.PendingChange(change.kind(), change.key(), toModel(change.change()));
    }

    private com.processpuzzle.release.model.ReleaseProblem toModel(ReleaseProblem problem) {
        return new com.processpuzzle.release.model.ReleaseProblem(
                problem.isError()
                        ? com.processpuzzle.release.model.ReleaseProblem.SeverityEnum.ERROR
                        : com.processpuzzle.release.model.ReleaseProblem.SeverityEnum.WARNING,
                problem.kind(), problem.key(), problem.errorId(), problem.message());
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
