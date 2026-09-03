package com.processpuzzle.platformadmin.usecase;

import com.processpuzzle.platformadmin.domain.Organization;
import com.processpuzzle.platformadmin.domain.OrganizationRepository;
import com.processpuzzle.platformadmin.domain.OrganizationStatus;
import com.processpuzzle.platformadmin.domain.event.OrganizationProvisionedEvent;
import com.processpuzzle.platformadmin.usecase.exception.OrganizationAlreadyExistsException;
import com.processpuzzle.platformadmin.usecase.exception.OrganizationKeyInvalidException;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Claiming the key and committing the row. The starter {@code AppDefinition} that used to be written
 * in the same call is base-app's now — see {@code ProvisionTenantTest} there for the half of this
 * class's original assertions that went with it.
 */
class ProvisionOrganizationTest {

    private static final OrganizationDetails DETAILS =
            new OrganizationDetails("My Organization Ltd.", null, "ops@my-org.example", "en-GB");

    private OrganizationRepository organizationRepository;
    private ApplicationEventPublisher events;
    private ProvisionOrganization provisionOrganization;

    @BeforeEach
    void setUp() {
        organizationRepository = mock(OrganizationRepository.class);
        events = mock(ApplicationEventPublisher.class);
        when(organizationRepository.existsById(anyString())).thenReturn(false);
        when(organizationRepository.save(any(Organization.class))).thenAnswer(call -> call.getArgument(0));

        CheckOrganizationKey checkOrganizationKey =
                new CheckOrganizationKey(organizationRepository, new ReservedOrganizationKeys(List.of(), null));
        provisionOrganization =
                new ProvisionOrganization(organizationRepository, checkOrganizationKey, events);
    }

    /**
     * {@code PROVISIONING}, not {@code ACTIVE} — the tenant's Keycloak realm does not exist yet, and
     * a tenant marked usable before its realm exists is one whose members cannot log in.
     */
    @Test
    void commitsTheTenantAsProvisioningRatherThanActive() {
        Organization organization = provisionOrganization.execute("my-org", DETAILS);

        assertThat(organization.getKey()).isEqualTo("my-org");
        assertThat(organization.getName()).isEqualTo("My Organization Ltd.");
        assertThat(organization.getStatus()).isEqualTo(OrganizationStatus.PROVISIONING);
        assertThat(organization.getDefaultLocale()).isEqualTo("en-GB");
    }

    /**
     * The realm call cannot happen inside this transaction, so the use case announces the need and a
     * separate after-commit handler acts on it. Without this event a tenant would sit in
     * {@code PROVISIONING} forever.
     */
    @Test
    void announcesThatARealmIsNeeded() {
        provisionOrganization.execute("my-org", DETAILS);

        verify(events).publishEvent(
                new OrganizationProvisionedEvent("my-org", "My Organization Ltd.", "en-GB"));
    }

    @Test
    void keyIsNormalisedBeforeUse() {
        assertThat(provisionOrganization.execute("  My-Org  ", DETAILS).getKey()).isEqualTo("my-org");
    }

    @Test
    void takenKey_conflictsAndPersistsNothing() {
        when(organizationRepository.existsById("my-org")).thenReturn(true);

        assertThatThrownBy(() -> provisionOrganization.execute("my-org", DETAILS))
                .isInstanceOf(OrganizationAlreadyExistsException.class);

        verify(organizationRepository, never()).save(any());
        verifyNoInteractions(events);
    }

    @Test
    void reservedKey_isRejectedAsBadRequestNotConflict() {
        assertThatThrownBy(() -> provisionOrganization.execute("api", DETAILS))
                .isInstanceOf(OrganizationKeyInvalidException.class)
                .extracting(ex -> ((OrganizationKeyInvalidException) ex).getErrorId())
                .isEqualTo("organization.key.reserved");

        verify(organizationRepository, never()).save(any());
    }

    /** {@code platform} joined the reserved list when the staff API claimed that namespace. */
    @Test
    void thePlatformNamespaceCannotBeClaimedByATenant() {
        assertThatThrownBy(() -> provisionOrganization.execute("platform", DETAILS))
                .isInstanceOf(OrganizationKeyInvalidException.class)
                .extracting(ex -> ((OrganizationKeyInvalidException) ex).getErrorId())
                .isEqualTo("organization.key.reserved");
    }

    @Test
    void malformedKey_isRejected() {
        assertThatThrownBy(() -> provisionOrganization.execute("My Org!", DETAILS))
                .isInstanceOf(OrganizationKeyInvalidException.class)
                .extracting(ex -> ((OrganizationKeyInvalidException) ex).getErrorId())
                .isEqualTo("organization.key.invalid");
    }

    /**
     * {@code key} is required by the contract, but bean validation runs on the adapter — the use case
     * still has to answer with a reason rather than a {@link NullPointerException}.
     */
    @Test
    void absentKey_isRejectedAsMissingRatherThanCrashing() {
        assertThatThrownBy(() -> provisionOrganization.execute(null, DETAILS))
                .isInstanceOf(OrganizationKeyInvalidException.class)
                .extracting(ex -> ((OrganizationKeyInvalidException) ex).getErrorId())
                .isEqualTo("organization.key.missing");

        verify(organizationRepository, never()).save(any(Organization.class));
    }
}
