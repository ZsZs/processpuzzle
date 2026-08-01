package com.processpuzzle.app.usecase;

import com.processpuzzle.app.domain.Organization;
import com.processpuzzle.app.domain.OrganizationRepository;
import com.processpuzzle.app.model.OrganizationUpdate;
import com.processpuzzle.app.usecase.exception.OrganizationNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Updates an organization's descriptive fields. The key is not among them: it is the public URL of
 * the tenant's application and the scope of all its metadata, so changing it would orphan every id.
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

    public Organization execute(String orgKey, OrganizationUpdate input) {
        guard.requireDesign(orgKey);
        Organization organization = repository.findById(orgKey)
                .orElseThrow(() -> new OrganizationNotFoundException(orgKey));

        organization.setName(input.getName());
        organization.setDescription(input.getDescription());
        organization.setContactEmail(input.getContactEmail());
        organization.setDefaultLocale(input.getDefaultLocale());

        return repository.save(organization);
    }
}
