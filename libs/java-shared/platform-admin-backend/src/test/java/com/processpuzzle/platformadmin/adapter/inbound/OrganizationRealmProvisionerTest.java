package com.processpuzzle.platformadmin.adapter.inbound;

import com.processpuzzle.platformadmin.PlatformAdminTestFixtures;
import com.processpuzzle.platformadmin.domain.Organization;
import com.processpuzzle.platformadmin.domain.OrganizationRepository;
import com.processpuzzle.platformadmin.domain.OrganizationStatus;
import com.processpuzzle.platformadmin.domain.event.OrganizationDeletedEvent;
import com.processpuzzle.platformadmin.domain.event.OrganizationProvisionedEvent;
import com.processpuzzle.core.identity.IdentityProviderUnavailableException;
import com.processpuzzle.platformadmin.usecase.port.IdentityRealmPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.util.Optional;

import static com.processpuzzle.platformadmin.PlatformAdminTestFixtures.ORG_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The step that turns a {@code PROVISIONING} row into a usable tenant, and the one place in this
 * module where a transaction annotation is load-bearing rather than routine — so two of these tests
 * assert on the annotations themselves. That is unusual, and deliberate: getting either wrong loses
 * the status write silently, with the creating request still answering 201 and the loss only visible
 * on a later read.
 */
class OrganizationRealmProvisionerTest {

    private static final OrganizationProvisionedEvent PROVISIONED =
            new OrganizationProvisionedEvent(ORG_KEY, "My Organization Ltd.", "en-GB");
    /** The stack this deployment serves — deliberately not {@link #ORG_KEY}, which is a tenant. */
    private static final String STACK_KEY = "processpuzzle-testbed";
    private static final OrganizationProvisionedEvent OWN_STACK_PROVISIONED =
            new OrganizationProvisionedEvent(STACK_KEY, "ProcessPuzzle Testbed", "en-GB");

    private OrganizationRepository repository;
    private IdentityRealmPort realms;
    private OrganizationRealmProvisioner provisioner;

    @BeforeEach
    void setUp() {
        repository = mock(OrganizationRepository.class);
        realms = mock(IdentityRealmPort.class);
        when(repository.save(any(Organization.class))).thenAnswer(call -> call.getArgument(0));
        provisioner = new OrganizationRealmProvisioner(repository, realms, STACK_KEY);
    }

    @Test
    void createsTheRealmAndThenFlipsTheTenantToActive() {
        Organization organization = PlatformAdminTestFixtures.organization(OrganizationStatus.PROVISIONING);
        when(repository.findById(ORG_KEY)).thenReturn(Optional.of(organization));

        provisioner.onOrganizationProvisioned(PROVISIONED);

        verify(realms).createRealm(ORG_KEY, "My Organization Ltd.", "en-GB");
        assertThat(organization.getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
        verify(repository).save(organization);
    }

    /**
     * The tenant stays PROVISIONING, which <em>is</em> the error report: visible in the list, and
     * retryable. Nothing is rethrown because the originating request was answered long ago and there
     * is no caller left to inform.
     */
    @Test
    void aFailedRealmCallLeavesTheTenantProvisioningAndDoesNotThrow() {
        Organization organization = PlatformAdminTestFixtures.organization(OrganizationStatus.PROVISIONING);
        when(repository.findById(ORG_KEY)).thenReturn(Optional.of(organization));
        doThrow(new IdentityProviderUnavailableException("down"))
                .when(realms).createRealm(anyString(), anyString(), anyString());

        assertThatCode(() -> provisioner.onOrganizationProvisioned(PROVISIONED)).doesNotThrowAnyException();

        assertThat(organization.getStatus()).isEqualTo(OrganizationStatus.PROVISIONING);
        verify(repository, never()).save(any());
    }

    /** Deleted between the commit and this handler running — rare, and not worth failing over. */
    @Test
    void aTenantThatDisappearedIsToleratedAfterTheRealmWasCreated() {
        when(repository.findById(ORG_KEY)).thenReturn(Optional.empty());

        assertThatCode(() -> provisioner.onOrganizationProvisioned(PROVISIONED)).doesNotThrowAnyException();

        verify(realms).createRealm(ORG_KEY, "My Organization Ltd.", "en-GB");
        verify(repository, never()).save(any());
    }

    @Test
    void deletesTheRealmWhenTheTenantIsDeleted() {
        provisioner.onOrganizationDeleted(new OrganizationDeletedEvent(ORG_KEY));

        verify(realms).deleteRealm(ORG_KEY);
    }

    /**
     * An orphaned realm is inert and removable by hand; a live tenant with no identity provider is
     * not recoverable that way. So the failure is logged and swallowed rather than rolling anything
     * back.
     */
    @Test
    void aFailedRealmDeletionIsLoggedRatherThanRethrown() {
        doThrow(new IdentityProviderUnavailableException("down")).when(realms).deleteRealm(ORG_KEY);

        assertThatCode(() -> provisioner.onOrganizationDeleted(new OrganizationDeletedEvent(ORG_KEY)))
                .doesNotThrowAnyException();
    }

    /**
     * The stack's own realm comes from {@code tools/docker/keycloak/import/}, complete with the
     * stack's public client and users. Creating it here would bolt a tenant client and the org roles
     * onto it; the organization row still has to reach {@code ACTIVE}, because the realm it names is
     * already serving requests.
     */
    @Test
    void theStacksOwnRealmIsNotCreatedHereButTheOrganizationStillGoesActive() {
        Organization organization = PlatformAdminTestFixtures.organization(OrganizationStatus.PROVISIONING);
        when(repository.findById(STACK_KEY)).thenReturn(Optional.of(organization));

        provisioner.onOrganizationProvisioned(OWN_STACK_PROVISIONED);

        verify(realms, never()).createRealm(anyString(), anyString(), anyString());
        assertThat(organization.getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
        verify(repository).save(organization);
    }

    /**
     * The dangerous half. Deleting the stack's organization row would otherwise delete the realm every
     * user of the stack authenticates against — including the operator issuing the delete.
     */
    @Test
    void deletingTheStacksOwnOrganizationLeavesItsRealmAlone() {
        provisioner.onOrganizationDeleted(new OrganizationDeletedEvent(STACK_KEY));

        verify(realms, never()).deleteRealm(anyString());
    }

    /** Realm names are case-insensitive in Keycloak's URLs and org keys are normalised to lower case. */
    @Test
    void theOwnStackComparisonIgnoresCaseAndSurroundingSpace() {
        OrganizationRealmProvisioner padded =
                new OrganizationRealmProvisioner(repository, realms, "  ProcessPuzzle-Testbed  ");

        padded.onOrganizationDeleted(new OrganizationDeletedEvent(STACK_KEY));

        verify(realms, never()).deleteRealm(anyString());
    }

    /**
     * A deployment that serves no stack of its own — the property unset — must keep provisioning every
     * tenant, including one whose key happens to look like a stack name.
     */
    @Test
    void withNoOwnStackConfiguredEveryOrganizationIsProvisionedNormally() {
        OrganizationRealmProvisioner unconfigured =
                new OrganizationRealmProvisioner(repository, realms, null);
        when(repository.findById(STACK_KEY)).thenReturn(
                Optional.of(PlatformAdminTestFixtures.organization(OrganizationStatus.PROVISIONING)));

        unconfigured.onOrganizationProvisioned(OWN_STACK_PROVISIONED);
        unconfigured.onOrganizationDeleted(new OrganizationDeletedEvent(STACK_KEY));

        verify(realms).createRealm(STACK_KEY, "ProcessPuzzle Testbed", "en-GB");
        verify(realms).deleteRealm(STACK_KEY);
    }

    /**
     * Asserted because it is invisible in behaviour. {@code AFTER_COMMIT} is what keeps a network call
     * out of the database transaction; {@code REQUIRES_NEW} is what makes the resulting status write
     * land at all, since after-commit work on the original transaction is discarded without an error.
     */
    @Test
    void provisioningRunsAfterCommitInItsOwnTransaction() throws NoSuchMethodException {
        Method handler = OrganizationRealmProvisioner.class
                .getDeclaredMethod("onOrganizationProvisioned", OrganizationProvisionedEvent.class);

        assertThat(handler.getAnnotation(TransactionalEventListener.class))
                .isNotNull()
                .extracting(TransactionalEventListener::phase)
                .isEqualTo(TransactionPhase.AFTER_COMMIT);
        assertThat(handler.getAnnotation(Transactional.class))
                .isNotNull()
                .extracting(Transactional::propagation)
                .isEqualTo(Propagation.REQUIRES_NEW);
    }

    /**
     * Deliberately <em>not</em> {@code BEFORE_COMMIT}, unlike the data cleanup other features do on
     * this same event: a realm deletion cannot be rolled back if the transaction then fails.
     */
    @Test
    void realmDeletionRunsAfterCommit() throws NoSuchMethodException {
        Method handler = OrganizationRealmProvisioner.class
                .getDeclaredMethod("onOrganizationDeleted", OrganizationDeletedEvent.class);

        assertThat(handler.getAnnotation(TransactionalEventListener.class))
                .isNotNull()
                .extracting(TransactionalEventListener::phase)
                .isEqualTo(TransactionPhase.AFTER_COMMIT);
    }
}
