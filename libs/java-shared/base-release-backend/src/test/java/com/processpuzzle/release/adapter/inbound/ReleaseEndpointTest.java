package com.processpuzzle.release.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.processpuzzle.core.definition.PendingChange;
import com.processpuzzle.core.definition.ReleaseProblem;
import com.processpuzzle.core.security.CurrentPrincipal;
import com.processpuzzle.release.domain.OrgRelease;
import com.processpuzzle.release.domain.OrgReleaseItem;
import com.processpuzzle.release.model.DefinitionChange;
import com.processpuzzle.release.model.Release;
import com.processpuzzle.release.model.ReleaseItem;
import com.processpuzzle.release.model.ReleaseRequest;
import com.processpuzzle.release.usecase.DescribePendingRelease;
import com.processpuzzle.core.definition.MigrationStep;
import com.processpuzzle.release.usecase.ListReleases;
import com.processpuzzle.release.usecase.ManagePendingSteps;
import com.processpuzzle.release.usecase.PendingRelease;
import com.processpuzzle.release.usecase.ReleaseOrganization;
import com.processpuzzle.release.usecase.ReleaseRecord;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ReleaseEndpointTest {

    private static final String ORG = "my-org";
    private static final Instant RELEASED_AT = Instant.parse("2026-10-11T10:00:00Z");

    private final DescribePendingRelease describePendingRelease = mock(DescribePendingRelease.class);
    private final ReleaseOrganization releaseOrganization = mock(ReleaseOrganization.class);
    private final ListReleases listReleases = mock(ListReleases.class);
    private final ManagePendingSteps managePendingSteps = mock(ManagePendingSteps.class);
    private final CurrentPrincipal currentPrincipal = mock(CurrentPrincipal.class);
    private ReleaseEndpoint endpoint;

    @BeforeEach
    void setUp() {
        endpoint = new ReleaseEndpoint(describePendingRelease, releaseOrganization, listReleases, managePendingSteps,
                new ReleaseMapper(), currentPrincipal);
    }

    @Test
    void replacingThePendingStepsPassesThemOnWithoutAnId_andAnswersThemAsStored() {
        com.processpuzzle.release.model.MigrationStep sent =
                new com.processpuzzle.release.model.MigrationStep("entity", "customer", "renameAttribute");
        sent.setId("ignored");
        sent.setSubject("customerName");
        sent.setParameters(java.util.Map.of("to", "name"));
        MigrationStep stored = new MigrationStep("s-1", "entity", "customer", "renameAttribute", "customerName",
                java.util.Map.of("to", "name"));
        when(managePendingSteps.replace(ORG, List.of(new MigrationStep(null, "entity", "customer", "renameAttribute",
                "customerName", java.util.Map.of("to", "name"))))).thenReturn(List.of(stored));

        var body = endpoint.replacePendingSteps(ORG, List.of(sent)).getBody();

        assertThat(body).singleElement().satisfies(step -> {
            assertThat(step.getId()).isEqualTo("s-1");
            assertThat(step.getParameters()).containsEntry("to", "name");
        });
    }

    @Test
    void thePendingReleaseCarriesItsChangesAndProblems() {
        when(describePendingRelease.execute(ORG)).thenReturn(new PendingRelease(
                List.of(new PendingChange("module", "claims", PendingChange.Change.ADDED, "{}"),
                        new PendingChange("app", "app", PendingChange.Change.CHANGED, "{}")),
                List.of(ReleaseProblem.warning("app", "app", "release.unresolved-reference", "Mounts a missing module."),
                        ReleaseProblem.error("entity", "claim", "entity.release.attributeTypeChanged", "Changes its type.")
                                .about("amount", 3L))));

        var body = endpoint.getPendingRelease(ORG).getBody();

        assertThat(body).isNotNull();
        assertThat(body.getChanges()).extracting(change -> change.getChange())
                .containsExactly(DefinitionChange.ADDED, DefinitionChange.CHANGED);
        assertThat(body.getProblems()).extracting(problem -> problem.getSeverity(), problem -> problem.getSubject(),
                        problem -> problem.getAffectedObjects())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(com.processpuzzle.release.model.ReleaseProblem.SeverityEnum.WARNING, null, null),
                        org.assertj.core.groups.Tuple.tuple(com.processpuzzle.release.model.ReleaseProblem.SeverityEnum.ERROR, "amount", 3L));
    }

    @Test
    void releasingAnswers201WithTheReleaseAndPassesTheConfirmationAndThePrincipal() {
        when(currentPrincipal.displayName()).thenReturn(Optional.of("designer"));
        when(releaseOrganization.execute(ORG, true, "designer")).thenReturn(releaseRecord("designer"));

        var response = endpoint.releaseOrganization(ORG, new ReleaseRequest().acknowledgeWarnings(true));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Release release = response.getBody();
        assertThat(release).isNotNull();
        assertThat(release.getReleaseNumber()).isEqualTo(3L);
        assertThat(release.getReleasedAt()).isEqualTo(OffsetDateTime.ofInstant(RELEASED_AT, ZoneOffset.UTC));
        assertThat(release.getReleasedBy()).isEqualTo("designer");
        assertThat(release.getItems()).extracting(ReleaseItem::getKey, ReleaseItem::getChange, ReleaseItem::getContentHash)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("claims", DefinitionChange.CHANGED, "abc"),
                        org.assertj.core.groups.Tuple.tuple("orders", DefinitionChange.REMOVED, null));
    }

    @Test
    void releasingWithoutABodyOrAPrincipalConfirmsNothingAndRecordsNoOne() {
        when(currentPrincipal.displayName()).thenReturn(Optional.empty());
        when(releaseOrganization.execute(ORG, false, null)).thenReturn(releaseRecord(null));

        Release release = endpoint.releaseOrganization(ORG, null).getBody();

        assertThat(release).isNotNull();
        assertThat(release.getReleasedBy()).isNull();
    }

    @Test
    void theHistoryListsEveryRelease() {
        when(listReleases.execute(ORG)).thenReturn(List.of(releaseRecord("designer")));

        assertThat(endpoint.listReleases(ORG).getBody()).extracting(Release::getReleaseNumber).containsExactly(3L);
    }

    private static ReleaseRecord releaseRecord(String releasedBy) {
        return new ReleaseRecord(new OrgRelease(ORG, 3, RELEASED_AT, releasedBy), List.of(
                new OrgReleaseItem(ORG, 3, 0, "module", "claims", PendingChange.Change.CHANGED, "abc"),
                new OrgReleaseItem(ORG, 3, 1, "module", "orders", PendingChange.Change.REMOVED, null)));
    }
}
