package com.processpuzzle.platformadmin.usecase;

import com.processpuzzle.platformadmin.domain.Organization;
import com.processpuzzle.platformadmin.domain.OrganizationRepository;
import com.processpuzzle.platformadmin.domain.OrganizationStatus;
import com.processpuzzle.platformadmin.domain.event.OrganizationProvisionedEvent;
import com.processpuzzle.platformadmin.usecase.exception.OrganizationAlreadyExistsException;
import com.processpuzzle.platformadmin.usecase.exception.OrganizationKeyInvalidException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Claims a tenant key and commits the organization.
 *
 * <p>Creates <em>only</em> the {@link Organization}. It used to create the tenant's starter
 * {@code AppDefinition} in the same transaction, so that a client could never observe an
 * organization without an app to design — an invariant worth keeping. {@code AppDefinition} belongs
 * to base-app, which is now a consumer of this module, so the invariant moved rather than
 * disappeared: {@code app.usecase.ProvisionTenant} is {@code @Transactional}, calls this, and
 * creates the starter app in the transaction it opened. Both still commit together.
 *
 * <p>The organization is committed {@link OrganizationStatus#PROVISIONING}, not {@code ACTIVE}.
 * Its identity realm has to exist before a member can sign in, and creating one is an HTTP call to
 * Keycloak that must not hold a database transaction open — so this method publishes
 * {@link OrganizationProvisionedEvent} and {@code OrganizationRealmProvisioner} picks it up after
 * commit. That makes {@code PROVISIONING} observable, and a row stuck in it a diagnosable failure
 * rather than a silent one.
 */
@Service
public class ProvisionOrganization {

    /** The {@code errorId} {@link CheckOrganizationKey} answers with when the key is already claimed. */
    private static final String KEY_TAKEN = "organization.key.taken";

    private final OrganizationRepository organizationRepository;
    private final CheckOrganizationKey checkOrganizationKey;
    private final ApplicationEventPublisher events;

    public ProvisionOrganization(OrganizationRepository organizationRepository,
                                 CheckOrganizationKey checkOrganizationKey,
                                 ApplicationEventPublisher events) {
        this.organizationRepository = organizationRepository;
        this.checkOrganizationKey = checkOrganizationKey;
        this.events = events;
    }

    /**
     * @param key the requested tenant key; trimmed and lower-cased before it is checked
     * @param details display name and the rest of the descriptive fields
     * @throws OrganizationAlreadyExistsException when the key is taken
     * @throws OrganizationKeyInvalidException when the key is malformed or reserved
     */
    @Transactional
    public Organization execute(String key, OrganizationDetails details) {
        String orgKey = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        KeyCheckOutcome keyCheck = checkOrganizationKey.execute(orgKey);
        if (!keyCheck.available()) {
            if (KEY_TAKEN.equals(keyCheck.errorId())) {
                throw new OrganizationAlreadyExistsException(orgKey);
            }
            throw new OrganizationKeyInvalidException(keyCheck.errorId(),
                    "Organization key cannot be claimed: '" + orgKey + "' (" + keyCheck.errorId() + ").");
        }

        Organization organization = organizationRepository.save(new Organization(
                orgKey,
                details.name(),
                details.description(),
                details.contactEmail(),
                details.defaultLocale(),
                OrganizationStatus.PROVISIONING));

        events.publishEvent(new OrganizationProvisionedEvent(orgKey, details.name(), details.defaultLocale()));

        return organization;
    }
}
