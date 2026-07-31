package com.processpuzzle.app.usecase;

import com.processpuzzle.app.domain.Organization;
import com.processpuzzle.app.domain.OrganizationRepository;
import com.processpuzzle.app.usecase.exception.OrganizationNotFoundException;
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
}
