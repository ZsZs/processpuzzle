package com.processpuzzle.app.usecase;

import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.app.domain.ModuleDefinitionRepository;
import com.processpuzzle.app.domain.OrganizationRepository;
import com.processpuzzle.app.usecase.exception.OrganizationNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes a tenant and everything scoped by it.
 *
 * <p>The cascade is explicit rather than a JPA {@code @OneToMany} with {@code cascade = REMOVE}: an
 * association from {@code Organization} to {@code AppDefinition} would have to join on the composite
 * key pair and would buy nothing else, since nothing navigates from an organization to its apps
 * in-memory.
 *
 * <p>Only app definitions and modules are removed today. Entity descriptors, rules, state and workflow
 * definitions are also organization-scoped by contract, but none of those features has an
 * organization-aware backend yet — when they do, their deletion belongs here (or, better, behind a
 * domain event this use case publishes).
 */
@Service
@Transactional
public class DeleteOrganization {

    private final OrganizationRepository organizationRepository;
    private final AppDefinitionRepository appDefinitionRepository;
    private final ModuleDefinitionRepository moduleDefinitionRepository;
    private final OrganizationGuard guard;

    public DeleteOrganization(OrganizationRepository organizationRepository,
                              AppDefinitionRepository appDefinitionRepository,
                              ModuleDefinitionRepository moduleDefinitionRepository,
                              OrganizationGuard guard) {
        this.organizationRepository = organizationRepository;
        this.appDefinitionRepository = appDefinitionRepository;
        this.moduleDefinitionRepository = moduleDefinitionRepository;
        this.guard = guard;
    }

    public void execute(String orgKey) {
        guard.requireDesign(orgKey);
        if (!organizationRepository.existsById(orgKey)) {
            throw new OrganizationNotFoundException(orgKey);
        }
        appDefinitionRepository.deleteByOrgKey(orgKey);
        moduleDefinitionRepository.deleteByOrgKey(orgKey);
        organizationRepository.deleteById(orgKey);
    }
}
