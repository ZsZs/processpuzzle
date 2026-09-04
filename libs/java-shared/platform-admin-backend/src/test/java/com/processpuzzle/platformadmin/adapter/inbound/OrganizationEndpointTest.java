package com.processpuzzle.platformadmin.adapter.inbound;

import com.processpuzzle.platformadmin.PlatformAdminTestFixtures;
import com.processpuzzle.platformadmin.model.KeyAvailability;
import com.processpuzzle.platformadmin.model.Organization;
import com.processpuzzle.platformadmin.model.OrganizationInput;
import com.processpuzzle.platformadmin.model.OrganizationStatus;
import com.processpuzzle.platformadmin.model.OrganizationUpdate;
import com.processpuzzle.platformadmin.usecase.CheckOrganizationKey;
import com.processpuzzle.platformadmin.usecase.DeleteOrganization;
import com.processpuzzle.platformadmin.usecase.FindOrganization;
import com.processpuzzle.platformadmin.usecase.KeyCheckOutcome;
import com.processpuzzle.platformadmin.usecase.OrganizationDetails;
import com.processpuzzle.platformadmin.usecase.ProvisionOrganization;
import com.processpuzzle.platformadmin.usecase.UpdateOrganization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static com.processpuzzle.platformadmin.PlatformAdminTestFixtures.ORG_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The tenant-facing organization surface, which base-app's {@code AppEndpoint} used to serve.
 *
 * <p>What is worth asserting at this level is that each operation reaches the right use case and
 * answers the status the contract declares — the controller holds no logic of its own. The one
 * genuinely new behaviour is provisioning's response: it answers the organization alone, where the
 * operation it replaces answered an organization plus the starter app definition base-app wrote in
 * the same transaction. That definition is still created, by base-app's own listener on
 * {@code OrganizationProvisionedEvent}; it is simply not this contract's to describe.
 */
class OrganizationEndpointTest {

    private ProvisionOrganization provisionOrganization;
    private CheckOrganizationKey checkOrganizationKey;
    private FindOrganization findOrganization;
    private UpdateOrganization updateOrganization;
    private DeleteOrganization deleteOrganization;
    private OrganizationEndpoint endpoint;

    @BeforeEach
    void setUp() {
        provisionOrganization = mock(ProvisionOrganization.class);
        checkOrganizationKey = mock(CheckOrganizationKey.class);
        findOrganization = mock(FindOrganization.class);
        updateOrganization = mock(UpdateOrganization.class);
        deleteOrganization = mock(DeleteOrganization.class);
        endpoint = new OrganizationEndpoint(provisionOrganization, checkOrganizationKey, findOrganization,
                updateOrganization, deleteOrganization, new PlatformAdminMapper());
    }

    @Test
    void provisioningAnswers201WithTheOrganizationAlone() {
        when(provisionOrganization.execute(eq(ORG_KEY), any())).thenReturn(
                PlatformAdminTestFixtures.organization());

        ResponseEntity<Organization> response =
                endpoint.provisionOrganization(new OrganizationInput(ORG_KEY, "My Organization Ltd."));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull()
                .satisfies(body -> assertThat(body.getKey()).isEqualTo(ORG_KEY));
    }

    /**
     * The key is passed to the use case separately rather than inside the details record. It is the
     * aggregate's identity, and keeping it out of {@link OrganizationDetails} is what stops a later
     * update path from treating it as one more editable field.
     */
    @Test
    void provisioningForwardsTheKeySeparatelyFromTheDescriptiveFields() {
        OrganizationInput input = new OrganizationInput(ORG_KEY, "My Organization Ltd.");
        input.setDescription("Insurance.");
        input.setContactEmail("ops@my-org.example");
        input.setDefaultLocale("en-GB");
        when(provisionOrganization.execute(any(), any())).thenReturn(PlatformAdminTestFixtures.organization());

        endpoint.provisionOrganization(input);

        verify(provisionOrganization).execute(ORG_KEY, new OrganizationDetails(
                "My Organization Ltd.", "Insurance.", "ops@my-org.example", "en-GB"));
    }

    @Test
    void checkingAKeyAnswersTheAvailabilityWithItsReasonAndSuggestions() {
        when(checkOrganizationKey.execute("api")).thenReturn(
                KeyCheckOutcome.unavailable("api", "organization.key.reserved", List.of("api-app")));

        ResponseEntity<KeyAvailability> response = endpoint.checkOrganizationKey("api");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().satisfies(body -> {
            assertThat(body.getAvailable()).isFalse();
            assertThat(body.getErrorId()).isEqualTo("organization.key.reserved");
            assertThat(body.getSuggestions()).containsExactly("api-app");
        });
    }

    @Test
    void readingAnOrganizationAnswersItsModel() {
        when(findOrganization.execute(ORG_KEY)).thenReturn(PlatformAdminTestFixtures.organization());

        assertThat(endpoint.getOrganization(ORG_KEY).getBody()).isNotNull()
                .satisfies(body -> {
                    assertThat(body.getKey()).isEqualTo(ORG_KEY);
                    assertThat(body.getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
                });
    }

    @Test
    void updatingForwardsTheMappedDetailsAndAnswersTheUpdatedModel() {
        OrganizationUpdate input = new OrganizationUpdate("My Organization GmbH");
        input.setDescription("Now German.");
        input.setContactEmail("ops@my-org.example");
        input.setDefaultLocale("de-DE");
        OrganizationDetails expected = new OrganizationDetails("My Organization GmbH", "Now German.",
                "ops@my-org.example", "de-DE");
        when(updateOrganization.execute(ORG_KEY, expected)).thenReturn(PlatformAdminTestFixtures.organization());

        assertThat(endpoint.updateOrganization(ORG_KEY, input).getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(updateOrganization).execute(ORG_KEY, expected);
    }

    @Test
    void deletingAnswers204WithNoBody() {
        ResponseEntity<Void> response = endpoint.deleteOrganization(ORG_KEY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        verify(deleteOrganization).execute(ORG_KEY);
    }
}
