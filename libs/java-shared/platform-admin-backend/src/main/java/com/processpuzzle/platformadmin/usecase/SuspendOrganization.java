package com.processpuzzle.platformadmin.usecase;

import com.processpuzzle.core.tenancy.OrganizationGuard;
import com.processpuzzle.platformadmin.domain.Organization;
import com.processpuzzle.platformadmin.domain.OrganizationRepository;
import com.processpuzzle.platformadmin.domain.OrganizationStatus;
import com.processpuzzle.platformadmin.usecase.exception.OrganizationNotFoundException;
import com.processpuzzle.platformadmin.usecase.port.IdentityRealmPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revokes a tenant's access while retaining its data.
 *
 * <p>Two halves, and both are needed: the status stops the API answering, and disabling the realm
 * stops a member obtaining a token in the first place. Without the second, an already-issued token
 * keeps working until it expires and a fresh login still succeeds.
 *
 * <p><b>Realm first, then the row.</b> The realm call is not transactional, so if it fails the
 * status must not have changed — a tenant marked {@code SUSPENDED} whose members can still log in is
 * worse than one that is visibly still {@code ACTIVE}. Doing it in this order makes the failure mode
 * "nothing happened", which is retryable.
 *
 * <p>Idempotent: suspending an already-suspended tenant re-disables the realm and returns 200. That
 * is not laziness — it is the behaviour that lets an operator re-run the operation after a realm
 * call failed halfway.
 */
@Service
public class SuspendOrganization {

    private final OrganizationRepository repository;
    private final OrganizationGuard guard;
    private final IdentityRealmPort realms;

    public SuspendOrganization(OrganizationRepository repository, OrganizationGuard guard,
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

        realms.disableRealm(orgKey);

        organization.setStatus(OrganizationStatus.SUSPENDED);
        return repository.save(organization);
    }
}
