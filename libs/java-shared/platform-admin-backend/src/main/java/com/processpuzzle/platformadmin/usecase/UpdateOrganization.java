package com.processpuzzle.platformadmin.usecase;

import com.processpuzzle.core.tenancy.OrganizationGuard;
import com.processpuzzle.platformadmin.domain.Organization;
import com.processpuzzle.platformadmin.domain.OrganizationRepository;
import com.processpuzzle.platformadmin.usecase.exception.OrganizationNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Updates an organization's descriptive fields. The key is not among them: it is the public URL of
 * the tenant's application and the scope of all its metadata, so changing it would orphan every id.
 *
 * <p>The status is not among them either, and deliberately not settable here — a client that could
 * write {@code status: ACTIVE} would hand out a tenant with no identity provider behind it.
 * {@code SuspendOrganization} and {@code ActivateOrganization} own that transition, because each has
 * a realm call to make alongside the write.
 */
@Service
@Transactional
public class UpdateOrganization {

    private final OrganizationRepository repository;
    private final OrganizationGuard guard;

    public UpdateOrganization(OrganizationRepository repository, OrganizationGuard guard) {
        this.repository = repository;
        this.guard = guard;
    }

    public Organization execute(String orgKey, OrganizationDetails details) {
        guard.requireDesign(orgKey);
        return apply(orgKey, details);
    }

    /** As {@link #execute}, for the {@code /platform/**} caller already gated on staff authority. */
    public Organization executeAsPlatformAdmin(String orgKey, OrganizationDetails details) {
        guard.requirePlatformAdmin();
        return apply(orgKey, details);
    }

    private Organization apply(String orgKey, OrganizationDetails details) {
        Organization organization = repository.findById(orgKey)
                .orElseThrow(() -> new OrganizationNotFoundException(orgKey));

        organization.setName(details.name());
        organization.setDescription(details.description());
        organization.setContactEmail(details.contactEmail());
        organization.setDefaultLocale(details.defaultLocale());

        return repository.save(organization);
    }
}
