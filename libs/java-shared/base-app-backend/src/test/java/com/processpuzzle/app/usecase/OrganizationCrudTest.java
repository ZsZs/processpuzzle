package com.processpuzzle.app.usecase;

import com.processpuzzle.app.AppTestFixtures;
import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.app.domain.Organization;
import com.processpuzzle.app.domain.OrganizationRepository;
import com.processpuzzle.app.domain.OrganizationStatus;
import com.processpuzzle.app.model.OrganizationUpdate;
import com.processpuzzle.app.usecase.exception.OrganizationAccessDeniedException;
import com.processpuzzle.app.usecase.exception.OrganizationNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.junit.jupiter.api.Nested;

import java.util.Optional;

import static com.processpuzzle.app.AppTestFixtures.ORG_KEY;
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
class OrganizationCrudTest {

    private OrganizationRepository organizationRepository;
    private AppDefinitionRepository appDefinitionRepository;

    @BeforeEach
    void setUp() {
        organizationRepository = mock(OrganizationRepository.class);
        appDefinitionRepository = mock(AppDefinitionRepository.class);
        when(organizationRepository.save(any(Organization.class))).thenAnswer(call -> call.getArgument(0));
        when(organizationRepository.existsById(anyString())).thenReturn(true);
    }

    @Nested
    class Find {

        @Test
        void returnsTheStoredTenant() {
            Organization stored = organization();
            when(organizationRepository.findById(ORG_KEY)).thenReturn(Optional.of(stored));

            assertThat(findOrganization(AppTestFixtures.permissiveGuard()).execute(ORG_KEY)).isSameAs(stored);
        }

        @Test
        void unknownKey_is404() {
            when(organizationRepository.findById("nope")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> findOrganization(AppTestFixtures.permissiveGuard()).execute("nope"))
                    .isInstanceOf(OrganizationNotFoundException.class)
                    .hasMessageContaining("nope");
        }

        /** Reading a tenant needs membership, not design rights — it is what the shell calls at startup. */
        @Test
        void requiresMembership() {
            assertThatThrownBy(() -> findOrganization(AppTestFixtures.denyingGuard()).execute(ORG_KEY))
                    .isInstanceOf(OrganizationAccessDeniedException.class);

            verifyNoInteractions(organizationRepository);
        }

        private FindOrganization findOrganization(OrganizationGuard guard) {
            return new FindOrganization(organizationRepository, guard);
        }
    }

    @Nested
    class Update {

        @Test
        void replacesTheDescriptiveFieldsAndLeavesTheKeyAlone() {
            Organization stored = organization();
            when(organizationRepository.findById(ORG_KEY)).thenReturn(Optional.of(stored));
            OrganizationUpdate input = new OrganizationUpdate("My Organization GmbH");
            input.setDescription("Now German.");
            input.setContactEmail("ops@my-org.example");
            input.setDefaultLocale("de-DE");

            Organization updated = updateOrganization(AppTestFixtures.permissiveGuard()).execute(ORG_KEY, input);

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
            Organization stored = organization();
            when(organizationRepository.findById(ORG_KEY)).thenReturn(Optional.of(stored));

            Organization updated = updateOrganization(AppTestFixtures.permissiveGuard())
                    .execute(ORG_KEY, new OrganizationUpdate("My Organization Ltd."));

            assertThat(updated.getDescription()).isNull();
            assertThat(updated.getContactEmail()).isNull();
            assertThat(updated.getDefaultLocale()).isNull();
        }

        @Test
        void unknownKey_is404() {
            when(organizationRepository.findById("nope")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> updateOrganization(AppTestFixtures.permissiveGuard())
                    .execute("nope", new OrganizationUpdate("Nope")))
                    .isInstanceOf(OrganizationNotFoundException.class);

            verify(organizationRepository, never()).save(any());
        }

        @Test
        void requiresDesignRights() {
            assertThatThrownBy(() -> updateOrganization(AppTestFixtures.denyingGuard())
                    .execute(ORG_KEY, new OrganizationUpdate("Nope")))
                    .isInstanceOf(OrganizationAccessDeniedException.class);

            verifyNoInteractions(organizationRepository);
        }

        private UpdateOrganization updateOrganization(OrganizationGuard guard) {
            return new UpdateOrganization(organizationRepository, guard);
        }
    }

    @Nested
    class Delete {

        /**
         * The cascade is explicit rather than a JPA {@code cascade = REMOVE}, so the order is part of
         * the behaviour: the tenant's app definitions go first, inside the same transaction.
         */
        @Test
        void removesTheTenantsAppDefinitionsBeforeTheTenantItself() {
            deleteOrganization(AppTestFixtures.permissiveGuard()).execute(ORG_KEY);

            InOrder order = inOrder(appDefinitionRepository, organizationRepository);
            order.verify(appDefinitionRepository).deleteByOrgKey(ORG_KEY);
            order.verify(organizationRepository).deleteById(ORG_KEY);
        }

        @Test
        void unknownKey_is404AndNothingIsCascaded() {
            when(organizationRepository.existsById("nope")).thenReturn(false);

            assertThatThrownBy(() -> deleteOrganization(AppTestFixtures.permissiveGuard()).execute("nope"))
                    .isInstanceOf(OrganizationNotFoundException.class);

            verifyNoInteractions(appDefinitionRepository);
            verify(organizationRepository, never()).deleteById(anyString());
        }

        @Test
        void requiresDesignRights() {
            assertThatThrownBy(() -> deleteOrganization(AppTestFixtures.denyingGuard()).execute(ORG_KEY))
                    .isInstanceOf(OrganizationAccessDeniedException.class);

            verifyNoInteractions(organizationRepository, appDefinitionRepository);
        }

        private DeleteOrganization deleteOrganization(OrganizationGuard guard) {
            return new DeleteOrganization(organizationRepository, appDefinitionRepository, guard);
        }
    }

    private static Organization organization() {
        return new Organization(ORG_KEY, "My Organization Ltd.", "Insurance.", "ops@my-org.example",
                "en-GB", OrganizationStatus.ACTIVE);
    }
}
