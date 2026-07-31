package com.processpuzzle.app.usecase;

import com.processpuzzle.app.domain.AppDefinition;
import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.app.domain.Organization;
import com.processpuzzle.app.domain.OrganizationRepository;
import com.processpuzzle.app.domain.OrganizationStatus;
import com.processpuzzle.app.model.OrganizationInput;
import com.processpuzzle.app.usecase.exception.OrganizationAlreadyExistsException;
import com.processpuzzle.app.usecase.exception.OrganizationKeyInvalidException;
import com.processpuzzle.app.usecase.service.ReservedOrganizationKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProvisionOrganizationTest {

    private OrganizationRepository organizationRepository;
    private AppDefinitionRepository appDefinitionRepository;
    private ProvisionOrganization provisionOrganization;

    @BeforeEach
    void setUp() {
        organizationRepository = mock(OrganizationRepository.class);
        appDefinitionRepository = mock(AppDefinitionRepository.class);
        when(organizationRepository.existsById(anyString())).thenReturn(false);
        when(organizationRepository.save(any(Organization.class))).thenAnswer(call -> call.getArgument(0));
        when(appDefinitionRepository.save(any(AppDefinition.class))).thenAnswer(call -> call.getArgument(0));

        CheckOrganizationKey checkOrganizationKey =
                new CheckOrganizationKey(organizationRepository, new ReservedOrganizationKeys(List.of()));
        provisionOrganization = new ProvisionOrganization(organizationRepository, appDefinitionRepository,
                checkOrganizationKey);
    }

    @Test
    void createsAnActiveOrganizationAndAStarterDraftApp() {
        OrganizationInput input = new OrganizationInput("my-org", "My Organization Ltd.");
        input.setContactEmail("ops@my-org.example");
        input.setDefaultLocale("en-GB");

        ProvisionOrganization.Result result = provisionOrganization.execute(input);

        Organization organization = result.organization();
        assertThat(organization.getKey()).isEqualTo("my-org");
        assertThat(organization.getName()).isEqualTo("My Organization Ltd.");
        assertThat(organization.getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
        assertThat(organization.getDefaultLocale()).isEqualTo("en-GB");

        AppDefinition starterApp = result.starterApp();
        assertThat(starterApp.getOrgKey()).isEqualTo("my-org");
        assertThat(starterApp.getId()).isEqualTo(ProvisionOrganization.STARTER_APP_ID);
        assertThat(starterApp.getName()).isEqualTo("My Organization Ltd.");
        assertThat(starterApp.getRevision()).isEqualTo(1L);
        assertThat(starterApp.isPublished()).isFalse();
        assertThat(starterApp.hasPublishedRevision()).isFalse();
    }

    @Test
    void starterAppIsAlmostEmpty_oneContentRegionAndNothingElse() {
        ProvisionOrganization.Result result =
                provisionOrganization.execute(new OrganizationInput("my-org", "My Org"));

        AppDefinition starterApp = result.starterApp();
        assertThat(starterApp.getDraftGraph().regions()).extracting(com.processpuzzle.app.domain.Region::type)
                .containsExactly("content");
        assertThat(starterApp.getDraftGraph().pages()).isEmpty();
        assertThat(starterApp.getDraftGraph().theme()).isNull();
        assertThat(starterApp.getDraftGraph().layout()).isNull();
    }

    @Test
    void keyIsNormalisedBeforeUse() {
        ProvisionOrganization.Result result =
                provisionOrganization.execute(new OrganizationInput("  My-Org  ", "My Org"));

        assertThat(result.organization().getKey()).isEqualTo("my-org");
        assertThat(result.starterApp().getOrgKey()).isEqualTo("my-org");
    }

    @Test
    void takenKey_conflictsAndPersistsNothing() {
        when(organizationRepository.existsById("my-org")).thenReturn(true);

        assertThatThrownBy(() -> provisionOrganization.execute(new OrganizationInput("my-org", "My Org")))
                .isInstanceOf(OrganizationAlreadyExistsException.class);

        verify(organizationRepository, never()).save(any());
        verify(appDefinitionRepository, never()).save(any());
    }

    @Test
    void reservedKey_isRejectedAsBadRequestNotConflict() {
        assertThatThrownBy(() -> provisionOrganization.execute(new OrganizationInput("api", "API")))
                .isInstanceOf(OrganizationKeyInvalidException.class)
                .extracting(ex -> ((OrganizationKeyInvalidException) ex).getErrorId())
                .isEqualTo("organization.key.reserved");

        verify(organizationRepository, never()).save(any());
    }

    @Test
    void malformedKey_isRejected() {
        assertThatThrownBy(() -> provisionOrganization.execute(new OrganizationInput("My Org!", "My Org")))
                .isInstanceOf(OrganizationKeyInvalidException.class)
                .extracting(ex -> ((OrganizationKeyInvalidException) ex).getErrorId())
                .isEqualTo("organization.key.invalid");
    }
}
