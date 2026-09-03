package com.processpuzzle.platformadmin.usecase;

import com.processpuzzle.platformadmin.PlatformAdminTestFixtures;
import com.processpuzzle.platformadmin.domain.Organization;
import com.processpuzzle.platformadmin.domain.OrganizationRepository;
import com.processpuzzle.platformadmin.domain.OrganizationStatus;
import com.processpuzzle.platformadmin.usecase.exception.IdentityProviderUnavailableException;
import com.processpuzzle.platformadmin.usecase.exception.OrganizationAccessDeniedException;
import com.processpuzzle.platformadmin.usecase.exception.OrganizationNotFoundException;
import com.processpuzzle.platformadmin.usecase.exception.OrganizationStatusConflictException;
import com.processpuzzle.platformadmin.usecase.port.IdentityRealmPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Optional;

import static com.processpuzzle.platformadmin.PlatformAdminTestFixtures.ORG_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Suspend and activate. Both are two writes to two systems with no transaction between them, so the
 * ordering assertions here are the correctness argument, not incidental detail: the realm call goes
 * first, so that a failure leaves the status unchanged and the operation simply retryable.
 */
class OrganizationLifecycleTest {

    private OrganizationRepository repository;
    private IdentityRealmPort realms;

    @BeforeEach
    void setUp() {
        repository = mock(OrganizationRepository.class);
        realms = mock(IdentityRealmPort.class);
        when(repository.save(any(Organization.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Nested
    class Suspend {

        @Test
        void disablesTheRealmAndThenMarksTheTenantSuspended() {
            given(OrganizationStatus.ACTIVE);

            Organization suspended = suspend(PlatformAdminTestFixtures.permissiveGuard()).execute(ORG_KEY);

            assertThat(suspended.getStatus()).isEqualTo(OrganizationStatus.SUSPENDED);
            InOrder order = inOrder(realms, repository);
            order.verify(realms).disableRealm(ORG_KEY);
            order.verify(repository).save(suspended);
        }

        /**
         * The failure mode that matters. A tenant recorded as SUSPENDED whose members can still log in
         * is worse than one visibly still ACTIVE, so a failed realm call must leave the row alone.
         */
        @Test
        void aFailedRealmCallLeavesTheStatusUntouched() {
            given(OrganizationStatus.ACTIVE);
            doThrow(new IdentityProviderUnavailableException("down")).when(realms).disableRealm(ORG_KEY);

            assertThatThrownBy(() -> suspend(PlatformAdminTestFixtures.permissiveGuard()).execute(ORG_KEY))
                    .isInstanceOf(IdentityProviderUnavailableException.class);

            verify(repository, never()).save(any());
        }

        /** Idempotent: re-suspending is how an operator recovers from a half-failed suspension. */
        @Test
        void suspendingAnAlreadySuspendedTenantIsNotAConflict() {
            given(OrganizationStatus.SUSPENDED);

            assertThat(suspend(PlatformAdminTestFixtures.permissiveGuard()).execute(ORG_KEY).getStatus())
                    .isEqualTo(OrganizationStatus.SUSPENDED);
            verify(realms).disableRealm(ORG_KEY);
        }

        @Test
        void unknownKey_is404AndNoRealmIsTouched() {
            when(repository.findById("nope")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> suspend(PlatformAdminTestFixtures.permissiveGuard()).execute("nope"))
                    .isInstanceOf(OrganizationNotFoundException.class);

            verifyNoInteractions(realms);
        }

        @Test
        void requiresStaffAuthority() {
            assertThatThrownBy(() -> suspend(PlatformAdminTestFixtures.denyingGuard()).execute(ORG_KEY))
                    .isInstanceOf(OrganizationAccessDeniedException.class);

            verifyNoInteractions(repository, realms);
        }

        private SuspendOrganization suspend(OrganizationGuard guard) {
            return new SuspendOrganization(repository, guard, realms);
        }
    }

    @Nested
    class Activate {

        @Test
        void enablesTheRealmAndThenMarksTheTenantActive() {
            given(OrganizationStatus.SUSPENDED);

            Organization activated = activate(PlatformAdminTestFixtures.permissiveGuard()).execute(ORG_KEY);

            assertThat(activated.getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
            InOrder order = inOrder(realms, repository);
            order.verify(realms).enableRealm(ORG_KEY);
            order.verify(repository).save(activated);
        }

        /**
         * A PROVISIONING tenant has no realm to enable. Flipping it to ACTIVE would produce exactly the
         * state the whole provisioning arrangement exists to prevent — a tenant the platform considers
         * usable and nobody can log into — so it is refused rather than accommodated.
         */
        @Test
        void aProvisioningTenantIsRefusedRatherThanActivated() {
            given(OrganizationStatus.PROVISIONING);

            assertThatThrownBy(() -> activate(PlatformAdminTestFixtures.permissiveGuard()).execute(ORG_KEY))
                    .isInstanceOf(OrganizationStatusConflictException.class)
                    .hasMessageContaining("PROVISIONING");

            verifyNoInteractions(realms);
            verify(repository, never()).save(any());
        }

        @Test
        void aFailedRealmCallLeavesTheStatusUntouched() {
            given(OrganizationStatus.SUSPENDED);
            doThrow(new IdentityProviderUnavailableException("down")).when(realms).enableRealm(ORG_KEY);

            assertThatThrownBy(() -> activate(PlatformAdminTestFixtures.permissiveGuard()).execute(ORG_KEY))
                    .isInstanceOf(IdentityProviderUnavailableException.class);

            verify(repository, never()).save(any());
        }

        @Test
        void requiresStaffAuthority() {
            assertThatThrownBy(() -> activate(PlatformAdminTestFixtures.denyingGuard()).execute(ORG_KEY))
                    .isInstanceOf(OrganizationAccessDeniedException.class);

            verifyNoInteractions(repository, realms);
        }

        private ActivateOrganization activate(OrganizationGuard guard) {
            return new ActivateOrganization(repository, guard, realms);
        }
    }

    private void given(OrganizationStatus status) {
        when(repository.findById(ORG_KEY))
                .thenReturn(Optional.of(PlatformAdminTestFixtures.organization(status)));
    }
}
