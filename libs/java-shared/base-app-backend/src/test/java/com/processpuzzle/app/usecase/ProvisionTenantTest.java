package com.processpuzzle.app.usecase;

import com.processpuzzle.app.domain.AppDefinition;
import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.app.model.OrganizationInput;
import com.processpuzzle.platformadmin.domain.Organization;
import com.processpuzzle.platformadmin.domain.OrganizationRepository;
import com.processpuzzle.platformadmin.domain.OrganizationStatus;
import com.processpuzzle.platformadmin.usecase.CheckOrganizationKey;
import com.processpuzzle.platformadmin.usecase.OrganizationDetails;
import com.processpuzzle.platformadmin.usecase.ProvisionOrganization;
import com.processpuzzle.platformadmin.usecase.exception.OrganizationAlreadyExistsException;
import com.processpuzzle.platformadmin.usecase.service.ReservedOrganizationKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The invariant this class exists to protect: a client never sees an organization without a starter
 * app to design. It used to be guaranteed by one use case writing both rows; the organization now
 * lives in {@code platform-admin}, so {@link ProvisionTenant} owns the transaction that spans the two
 * modules instead.
 *
 * <p>{@link ProvisionOrganization} is wired for real rather than mocked, because what is worth
 * proving is the composition — that a refused key stops the starter app from being written at all,
 * which a stubbed collaborator would not demonstrate.
 */
class ProvisionTenantTest {

    private OrganizationRepository organizationRepository;
    private AppDefinitionRepository appDefinitionRepository;
    private ProvisionTenant provisionTenant;

    @BeforeEach
    void setUp() {
        organizationRepository = mock(OrganizationRepository.class);
        appDefinitionRepository = mock(AppDefinitionRepository.class);
        when(organizationRepository.existsById(anyString())).thenReturn(false);
        when(organizationRepository.save(any(Organization.class))).thenAnswer(call -> call.getArgument(0));
        when(appDefinitionRepository.save(any(AppDefinition.class))).thenAnswer(call -> call.getArgument(0));

        CheckOrganizationKey checkOrganizationKey =
                new CheckOrganizationKey(organizationRepository, new ReservedOrganizationKeys(List.of(), null));
        ProvisionOrganization provisionOrganization = new ProvisionOrganization(
                organizationRepository, checkOrganizationKey, mock(ApplicationEventPublisher.class));
        provisionTenant = new ProvisionTenant(provisionOrganization, appDefinitionRepository);
    }

    @Test
    void createsTheTenantAndAStarterDraftAppTogether() {
        OrganizationInput input = new OrganizationInput("my-org", "My Organization Ltd.");
        input.setContactEmail("ops@my-org.example");
        input.setDefaultLocale("en-GB");

        ProvisionTenant.Result result = provisionTenant.execute(input);

        Organization organization = result.organization();
        assertThat(organization.getKey()).isEqualTo("my-org");
        assertThat(organization.getStatus()).isEqualTo(OrganizationStatus.PROVISIONING);

        AppDefinition starterApp = result.starterApp();
        assertThat(starterApp.getOrgKey()).isEqualTo("my-org");
        assertThat(starterApp.getId()).isEqualTo(ProvisionTenant.STARTER_APP_ID);
        assertThat(starterApp.getName()).isEqualTo("My Organization Ltd.");
        assertThat(starterApp.getRevision()).isEqualTo(1L);
        assertThat(starterApp.isPublished()).isFalse();
        assertThat(starterApp.hasPublishedRevision()).isFalse();
    }

    @Test
    void starterAppIsGenuinelyEmpty_noRegionsNoRoutesNoThemeNoLayout() {
        AppDefinition starterApp =
                provisionTenant.execute(new OrganizationInput("my-org", "My Org")).starterApp();

        assertThat(starterApp.getDraftGraph().regions()).isEmpty();
        assertThat(starterApp.getDraftGraph().routes()).isEmpty();
        assertThat(starterApp.getDraftGraph().theme()).isNull();
        assertThat(starterApp.getDraftGraph().layout()).isNull();
    }

    /**
     * The starter app is keyed by the <em>normalised</em> key the organization ended up with, not by
     * the raw input — otherwise the two rows would disagree and the app would be invisible.
     */
    @Test
    void theStarterAppUsesTheNormalisedKey() {
        ProvisionTenant.Result result = provisionTenant.execute(new OrganizationInput("  My-Org  ", "My Org"));

        assertThat(result.organization().getKey()).isEqualTo("my-org");
        assertThat(result.starterApp().getOrgKey()).isEqualTo("my-org");
    }

    @Test
    void aRefusedKeyWritesNeitherRow() {
        when(organizationRepository.existsById("my-org")).thenReturn(true);

        assertThatThrownBy(() -> provisionTenant.execute(new OrganizationInput("my-org", "My Org")))
                .isInstanceOf(OrganizationAlreadyExistsException.class);

        verify(organizationRepository, never()).save(any());
        verify(appDefinitionRepository, never()).save(any());
    }

    /** The organization use case takes the shared record, not base-app's DTO. */
    @Test
    void theInputIsMappedOntoTheUseCaseLevelRecord() {
        OrganizationInput input = new OrganizationInput("my-org", "My Org");
        input.setDescription("Insurance.");
        input.setContactEmail("ops@my-org.example");
        input.setDefaultLocale("en-GB");

        Organization organization = provisionTenant.execute(input).organization();

        assertThat(new OrganizationDetails(organization.getName(), organization.getDescription(),
                organization.getContactEmail(), organization.getDefaultLocale()))
                .isEqualTo(new OrganizationDetails("My Org", "Insurance.", "ops@my-org.example", "en-GB"));
    }
}
