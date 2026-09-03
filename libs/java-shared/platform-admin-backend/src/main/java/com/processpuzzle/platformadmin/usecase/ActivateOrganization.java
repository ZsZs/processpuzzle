package com.processpuzzle.platformadmin.usecase;

import com.processpuzzle.platformadmin.domain.Organization;
import com.processpuzzle.platformadmin.domain.OrganizationRepository;
import com.processpuzzle.platformadmin.domain.OrganizationStatus;
import com.processpuzzle.platformadmin.usecase.exception.OrganizationNotFoundException;
import com.processpuzzle.platformadmin.usecase.exception.OrganizationStatusConflictException;
import com.processpuzzle.platformadmin.usecase.port.IdentityRealmPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Restores a suspended tenant's access — the exact inverse of {@link SuspendOrganization}, in the
 * same order and for the same reason: re-enable the realm first, write the status second, so a failed
 * realm call leaves the tenant visibly suspended rather than nominally active but unusable.
 *
 * <p>Refuses a {@code PROVISIONING} tenant with 409. Its realm was never created, so there is
 * nothing to enable, and flipping the row to {@code ACTIVE} would produce exactly the state the
 * whole provisioning arrangement exists to prevent: a tenant the platform considers usable and
 * nobody can log into. Provisioning has to complete or be retried first.
 */
@Service
public class ActivateOrganization {

    private final OrganizationRepository repository;
    private final OrganizationGuard guard;
    private final IdentityRealmPort realms;

    public ActivateOrganization(OrganizationRepository repository, OrganizationGuard guard,
                                IdentityRealmPort realms) {
        this.repository = repository;
        this.guard = guard;
        this.realms = realms;
    }

    @Transactional
    public Organization execute(String orgKey) {
        guard.requirePlatformAdmin();
        Organization organization = repository.findById(orgKey)
                .orElseThrow(() -> new OrganizationNotFoundException(orgKey));

        if (organization.getStatus() == OrganizationStatus.PROVISIONING) {
            throw new OrganizationStatusConflictException(orgKey, organization.getStatus(), "activate");
        }

        realms.enableRealm(orgKey);

        organization.setStatus(OrganizationStatus.ACTIVE);
        return repository.save(organization);
    }
}
