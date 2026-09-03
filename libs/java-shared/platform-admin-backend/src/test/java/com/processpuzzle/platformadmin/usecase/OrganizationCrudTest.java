package com.processpuzzle.platformadmin.usecase;

import com.processpuzzle.core.tenancy.OrganizationGuard;
import com.processpuzzle.platformadmin.PlatformAdminTestFixtures;
import com.processpuzzle.platformadmin.domain.Organization;
import com.processpuzzle.platformadmin.domain.OrganizationRepository;
import com.processpuzzle.platformadmin.domain.OrganizationStatus;
import com.processpuzzle.platformadmin.domain.event.OrganizationDeletedEvent;
import com.processpuzzle.core.tenancy.OrganizationAccessDeniedException;
import com.processpuzzle.platformadmin.usecase.exception.OrganizationNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static com.processpuzzle.platformadmin.PlatformAdminTestFixtures.ORG_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Reading, updating and deleting a tenant. */
@SuppressWarnings("java:S5778")
class OrganizationCrudTest {

    private OrganizationRepository organizationRepository;
    private ApplicationEventPublisher events;

    @BeforeEach
    void setUp() {
        organizationRepository = mock(OrganizationRepository.class);
        events = mock(ApplicationEventPublisher.class);
        when(organizationRepository.save(any(Organization.class))).thenAnswer(call -> call.getArgument(0));
        when(organizationRepository.existsById(anyString())).thenReturn(true);
    }

    @Nested
    class Find {

        @Test
        void returnsTheStoredTenant() {
            Organization stored = PlatformAdminTestFixtures.organization();
            when(organizationRepository.findById(ORG_KEY)).thenReturn(Optional.of(stored));

            assertThat(findOrganization(PlatformAdminTestFixtures.permissiveGuard()).execute(ORG_KEY))
                    .isSameAs(stored);
        }

        @Test
        void unknownKey_is404() {
            when(organizationRepository.findById("nope")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> findOrganization(PlatformAdminTestFixtures.permissiveGuard()).execute("nope"))
                    .isInstanceOf(OrganizationNotFoundException.class)
                    .hasMessageContaining("nope");
        }

        /** Reading a tenant needs membership, not design rights — it is what the shell calls at startup. */
        @Test
        void requiresMembership() {
            assertThatThrownBy(() -> findOrganization(PlatformAdminTestFixtures.denyingGuard()).execute(ORG_KEY))
                    .isInstanceOf(OrganizationAccessDeniedException.class);

            verifyNoInteractions(organizationRepository);
        }

        /**
         * The unguarded read exists for the two callers that cannot be authorized by membership: the
         * {@code /platform/**} endpoints, and the resource server resolving a tenant before there is a
         * principal at all. It must therefore not consult the guard even when the guard would refuse.
         */
        @Test
        void theUnguardedReadSkipsTheMembershipCheck() {
            Organization stored = PlatformAdminTestFixtures.organization();
            when(organizationRepository.findById(ORG_KEY)).thenReturn(Optional.of(stored));

            assertThat(findOrganization(PlatformAdminTestFixtures.denyingGuard()).executeUnguarded(ORG_KEY))
                    .isSameAs(stored);
        }

        @Test
        void theUnguardedReadStillReportsAnUnknownKeyAs404() {
            when(organizationRepository.findById("nope")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> findOrganization(PlatformAdminTestFixtures.permissiveGuard())
                    .executeUnguarded("nope"))
                    .isInstanceOf(OrganizationNotFoundException.class);
        }

        private FindOrganization findOrganization(OrganizationGuard guard) {
            return new FindOrganization(organizationRepository, guard);
        }
    }

    @Nested
    class Update {

        @Test
        void replacesTheDescriptiveFieldsAndLeavesTheKeyAlone() {
            Organization stored = PlatformAdminTestFixtures.organization();
            when(organizationRepository.findById(ORG_KEY)).thenReturn(Optional.of(stored));

            Organization updated = updateOrganization(PlatformAdminTestFixtures.permissiveGuard())
                    .execute(ORG_KEY, new OrganizationDetails("My Organization GmbH", "Now German.",
                            "ops@my-org.example", "de-DE"));

            assertThat(updated.getKey()).isEqualTo(ORG_KEY);
            assertThat(updated.getName()).isEqualTo("My Organization GmbH");
            assertThat(updated.getDescription()).isEqualTo("Now German.");
            assertThat(updated.getContactEmail()).isEqualTo("ops@my-org.example");
            assertThat(updated.getDefaultLocale()).isEqualTo("de-DE");
            assertThat(updated.getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
            verify(organizationRepository).save(stored);
        }

        /** An absent field clears the stored one: the contract's update is a replace, not a patch. */
        @Test
        void anAbsentFieldClearsTheStoredValue() {
            Organization stored = PlatformAdminTestFixtures.organization();
            when(organizationRepository.findById(ORG_KEY)).thenReturn(Optional.of(stored));

            Organization updated = updateOrganization(PlatformAdminTestFixtures.permissiveGuard())
                    .execute(ORG_KEY, new OrganizationDetails("My Organization Ltd.", null, null, null));

            assertThat(updated.getDescription()).isNull();
            assertThat(updated.getContactEmail()).isNull();
            assertThat(updated.getDefaultLocale()).isNull();
        }

        /**
         * The status is not a field of {@link OrganizationDetails} at all, so an update cannot move a
         * tenant into {@code ACTIVE} behind {@code ActivateOrganization}'s back — which would hand out
         * a tenant whose realm was never created.
         */
        @Test
        void anUpdateCannotChangeTheStatus() {
            Organization stored = PlatformAdminTestFixtures.organization(OrganizationStatus.SUSPENDED);
            when(organizationRepository.findById(ORG_KEY)).thenReturn(Optional.of(stored));

            Organization updated = updateOrganization(PlatformAdminTestFixtures.permissiveGuard())
                    .execute(ORG_KEY, new OrganizationDetails("Renamed", null, null, null));

            assertThat(updated.getStatus()).isEqualTo(OrganizationStatus.SUSPENDED);
        }

        @Test
        void unknownKey_is404() {
            when(organizationRepository.findById("nope")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> updateOrganization(PlatformAdminTestFixtures.permissiveGuard())
                    .execute("nope", new OrganizationDetails("Nope", null, null, null)))
                    .isInstanceOf(OrganizationNotFoundException.class);

            verify(organizationRepository, never()).save(any());
        }

        @Test
        void requiresDesignRights() {
            assertThatThrownBy(() -> updateOrganization(PlatformAdminTestFixtures.denyingGuard())
                    .execute(ORG_KEY, new OrganizationDetails("Nope", null, null, null)))
                    .isInstanceOf(OrganizationAccessDeniedException.class);

            verifyNoInteractions(organizationRepository);
        }

        @Test
        void theStaffVariantRequiresStaffAuthorityInsteadOfDesignRights() {
            assertThatThrownBy(() -> updateOrganization(PlatformAdminTestFixtures.denyingGuard())
                    .executeAsPlatformAdmin(ORG_KEY, new OrganizationDetails("Nope", null, null, null)))
                    .isInstanceOf(OrganizationAccessDeniedException.class);

            Organization stored = PlatformAdminTestFixtures.organization();
            when(organizationRepository.findById(ORG_KEY)).thenReturn(Optional.of(stored));
            assertThat(updateOrganization(PlatformAdminTestFixtures.permissiveGuard())
                    .executeAsPlatformAdmin(ORG_KEY, new OrganizationDetails("Renamed", null, null, null))
                    .getName()).isEqualTo("Renamed");
        }

        private UpdateOrganization updateOrganization(OrganizationGuard guard) {
            return new UpdateOrganization(organizationRepository, guard);
        }
    }

    @Nested
    class Delete {

        /**
         * The cascade is an event rather than a set of repository calls, so what this asserts is that
         * the event is published <em>before</em> the row goes: a listener cleaning up after a tenant may
         * still need to read it. Listeners join this transaction via {@code BEFORE_COMMIT}, so both
         * halves commit together — see {@code OrganizationDeletedEvent}.
         */
        @Test
        void announcesTheDeletionBeforeRemovingTheTenantItself() {
            deleteOrganization(PlatformAdminTestFixtures.permissiveGuard()).execute(ORG_KEY);

            InOrder order = inOrder(events, organizationRepository);
            order.verify(events).publishEvent(new OrganizationDeletedEvent(ORG_KEY));
            order.verify(organizationRepository).deleteById(ORG_KEY);
        }

        @Test
        void unknownKey_is404AndNothingIsAnnounced() {
            when(organizationRepository.existsById("nope")).thenReturn(false);

            assertThatThrownBy(() -> deleteOrganization(PlatformAdminTestFixtures.permissiveGuard()).execute("nope"))
                    .isInstanceOf(OrganizationNotFoundException.class);

            verifyNoInteractions(events);
            verify(organizationRepository, never()).deleteById(anyString());
        }

        @Test
        void requiresDesignRights() {
            assertThatThrownBy(() -> deleteOrganization(PlatformAdminTestFixtures.denyingGuard()).execute(ORG_KEY))
                    .isInstanceOf(OrganizationAccessDeniedException.class);

            verifyNoInteractions(organizationRepository, events);
        }

        @Test
        void theStaffVariantRequiresStaffAuthorityInsteadOfDesignRights() {
            assertThatThrownBy(() -> deleteOrganization(PlatformAdminTestFixtures.denyingGuard())
                    .executeAsPlatformAdmin(ORG_KEY))
                    .isInstanceOf(OrganizationAccessDeniedException.class);

            deleteOrganization(PlatformAdminTestFixtures.permissiveGuard()).executeAsPlatformAdmin(ORG_KEY);
            verify(organizationRepository).deleteById(ORG_KEY);
        }

        private DeleteOrganization deleteOrganization(OrganizationGuard guard) {
            return new DeleteOrganization(organizationRepository, guard, events);
        }
    }
}
