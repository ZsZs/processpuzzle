package com.processpuzzle.release.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import com.processpuzzle.core.definition.PendingChange;
import com.processpuzzle.core.definition.ReleaseProblem;
import com.processpuzzle.release.model.DefinitionChange;
import com.processpuzzle.release.usecase.PendingRelease;
import com.processpuzzle.release.usecase.ReleaseRefusedException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ReleaseApiExceptionHandlerTest {

    private final ReleaseApiExceptionHandler handler = new ReleaseApiExceptionHandler(new ReleaseMapper());

    @Test
    void aRefusedReleaseIs409WithWhatRefusedIt() {
        PendingRelease pending = new PendingRelease(
                List.of(new PendingChange("module", "claims", PendingChange.Change.REMOVED, null)),
                List.of(ReleaseProblem.error("app", "app", "release.unresolved-reference", "Missing.")));

        var response = handler.handleRefused(new ReleaseRefusedException("my-org", pending, ReleaseRefusedException.Reason.ERRORS));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getChanges()).singleElement()
                .satisfies(change -> assertThat(change.getChange()).isEqualTo(DefinitionChange.REMOVED));
        assertThat(response.getBody().getProblems()).singleElement().satisfies(problem -> {
            assertThat(problem.getSeverity()).isEqualTo(com.processpuzzle.release.model.ReleaseProblem.SeverityEnum.ERROR);
            assertThat(problem.getErrorId()).isEqualTo("release.unresolved-reference");
        });
    }
}
