package com.processpuzzle.app.adapter.inbound;

import com.processpuzzle.app.domain.AppDefinition;
import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.shared.event.OrganizationProvisionedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The invariant this class exists to protect: a client never sees an organization without a starter
 * app to design.
 *
 * <p>This was {@code ProvisionTenantTest}, which wired platform-admin's {@code ProvisionOrganization}
 * for real and proved the composition of the two writes. It cannot any more, and should not: base-app
 * does not call that use case, and wiring it here would be the compile dependency this refactoring
 * removed. What is provable at this level is that the reaction to the event is correct and that its
 * transaction phase is the one that keeps the two writes atomic — the second is asserted on the
 * annotation, because no unit test can observe a rollback that a wrong phase would cause.
 */
class StarterAppCreatorTest {

    private static final OrganizationProvisionedEvent EVENT =
            new OrganizationProvisionedEvent("my-org", "My Organization Ltd.", "en-GB");

    private AppDefinitionRepository repository;
    private StarterAppCreator creator;

    @BeforeEach
    void setUp() {
        repository = mock(AppDefinitionRepository.class);
        when(repository.save(any(AppDefinition.class))).thenAnswer(call -> call.getArgument(0));
        creator = new StarterAppCreator(repository);
    }

    @Test
    void createsAStarterDraftAppForTheProvisionedTenant() {
        creator.onOrganizationProvisioned(EVENT);

        AppDefinition starterApp = saved();
        assertThat(starterApp.getOrgKey()).isEqualTo("my-org");
        assertThat(starterApp.getId()).isEqualTo(StarterAppCreator.STARTER_APP_ID);
        assertThat(starterApp.getName()).isEqualTo("My Organization Ltd.");
        assertThat(starterApp.getRevision()).isEqualTo(1L);
        assertThat(starterApp.isPublished()).isFalse();
        assertThat(starterApp.hasPublishedRevision()).isFalse();
    }

    @Test
    void theStarterAppIsGenuinelyEmpty_noRegionsNoRoutesNoThemeNoLayout() {
        creator.onOrganizationProvisioned(EVENT);

        AppDefinition starterApp = saved();
        assertThat(starterApp.getDraftGraph().regions()).isEmpty();
        assertThat(starterApp.getDraftGraph().routes()).isEmpty();
        assertThat(starterApp.getDraftGraph().theme()).isNull();
        assertThat(starterApp.getDraftGraph().layout()).isNull();
    }

    /**
     * The event carries the key the organization was actually committed with, already normalised by
     * {@code ProvisionOrganization}. Nothing here re-normalises it: two independent normalisations
     * are two chances to disagree, and a starter app keyed differently from its tenant is invisible.
     */
    @Test
    void theStarterAppIsKeyedByTheEventsOrgKey() {
        creator.onOrganizationProvisioned(new OrganizationProvisionedEvent("my-org", "My Org", null));

        assertThat(saved().getOrgKey()).isEqualTo("my-org");
    }

    /**
     * {@code BEFORE_COMMIT} is what keeps the starter app in the provisioning transaction. An
     * {@code AFTER_COMMIT} listener's writes are discarded without an error, so the failure mode is a
     * tenant with no app and nothing logged anywhere — worth pinning even though it reads as testing
     * an annotation.
     */
    @Test
    void runsBeforeCommit_soTheTwoRowsCommitTogether() throws NoSuchMethodException {
        TransactionalEventListener listener = StarterAppCreator.class
                .getMethod("onOrganizationProvisioned", OrganizationProvisionedEvent.class)
                .getAnnotation(TransactionalEventListener.class);

        assertThat(listener).isNotNull();
        assertThat(listener.phase()).isEqualTo(TransactionPhase.BEFORE_COMMIT);
    }

    private AppDefinition saved() {
        org.mockito.ArgumentCaptor<AppDefinition> captor =
                org.mockito.ArgumentCaptor.forClass(AppDefinition.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
