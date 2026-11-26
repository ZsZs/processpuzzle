package com.processpuzzle.release.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.OrganizationReleaseRefusedException;
import com.processpuzzle.core.definition.OrganizationReleaser;
import com.processpuzzle.core.definition.PendingChange;
import com.processpuzzle.core.definition.ReleaseProblem;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The release on behalf of code that wrote the drafts itself, inside that code's transaction. */
class ReleaseOnBehalfTest {

    private final ReleaseOrganization release = mock(ReleaseOrganization.class);
    private final FakeParticipant entities = new FakeParticipant(DefinitionKinds.ENTITY, DefinitionKinds.ENTITY_ORDER, new ArrayList<>());
    private final PendingRecordsLoader records = mock(PendingRecordsLoader.class);
    private final ReleaseOnBehalf onBehalf = new ReleaseOnBehalf(release, ReleaseParticipants.of(List.of(entities)), records);

    @Test
    void releasesWhatIsPending() {
        entities.withDraft("item", PendingChange.Change.ADDED);

        assertThat(onBehalf.release("common", "staff")).isEqualTo(OrganizationReleaser.Outcome.RELEASED);
        verify(release).provision("common", "staff");
    }

    /** Seed records queued by an install whose definitions are released already still need a release to load them. */
    @Test
    void pendingRecordsAloneAreSomethingToRelease() {
        when(records.hasPending("common")).thenReturn(true);

        assertThat(onBehalf.release("common", "staff")).isEqualTo(OrganizationReleaser.Outcome.RELEASED);
        verify(release).provision("common", "staff");
    }

    /** The release use case refuses an empty release by throwing, which would roll the caller's transaction back. */
    @Test
    void nothingPendingIsNotAttemptedAndNotARefusal() {
        assertThat(onBehalf.release("common", "staff")).isEqualTo(OrganizationReleaser.Outcome.NOTHING_PENDING);
        verify(release, never()).provision(any(), any());
    }

    @Test
    void aRefusalCarriesItsProblemsToTheCaller() {
        entities.withDraft("item", PendingChange.Change.CHANGED);
        ReleaseProblem problem = ReleaseProblem.error("entity", "item", "release.common-member-references", "members refer to it");
        doThrow(new ReleaseRefusedException("common", new PendingRelease(List.of(), List.of(problem)), ReleaseRefusedException.Reason.ERRORS))
                .when(release).provision("common", "staff");

        assertThatThrownBy(() -> onBehalf.release("common", "staff")).isInstanceOfSatisfying(OrganizationReleaseRefusedException.class,
                refused -> assertThat(refused.problems()).containsExactly(problem));
    }
}
