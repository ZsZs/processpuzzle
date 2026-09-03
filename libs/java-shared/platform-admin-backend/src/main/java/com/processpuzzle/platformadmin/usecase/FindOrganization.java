package com.processpuzzle.platformadmin.usecase;

import com.processpuzzle.platformadmin.domain.Organization;
import com.processpuzzle.platformadmin.domain.OrganizationRepository;
import com.processpuzzle.platformadmin.usecase.exception.OrganizationNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FindOrganization {

    private final OrganizationRepository repository;
    private final OrganizationGuard guard;

    public FindOrganization(OrganizationRepository repository, OrganizationGuard guard) {
        this.repository = repository;
        this.guard = guard;
    }

    public Organization execute(String orgKey) {
        guard.requireAccess(orgKey);
        return repository.findById(orgKey).orElseThrow(() -> new OrganizationNotFoundException(orgKey));
    }

    /**
     * Reads an organization without the membership check, for callers already authorized by
     * something other than tenant membership.
     *
     * <p>Two of them: the {@code /platform/**} endpoints, gated on
     * {@link OrganizationGuard#requirePlatformAdmin()} instead — staff act across tenants, so
     * {@link #execute} would reject every call — and the resource server itself, which has to resolve
     * a tenant before there is an authenticated principal to check membership for. Keeping the
     * distinction in the method name rather than in a boolean flag is deliberate: an unguarded read
     * should be something a caller has to ask for by name.
     */
    public Organization executeUnguarded(String orgKey) {
        return repository.findById(orgKey).orElseThrow(() -> new OrganizationNotFoundException(orgKey));
    }
}
