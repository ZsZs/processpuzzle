package com.processpuzzle.app.usecase;

import com.processpuzzle.app.domain.ModuleDefinition;
import com.processpuzzle.app.domain.ModuleDefinitionRepository;
import com.processpuzzle.app.usecase.exception.ModuleDefinitionNotFoundException;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The lazy-load entry point: the shell fetches a module's routes when something navigates under the
 * {@code basePath} it is mounted at.
 *
 * <p>Guarded with {@code requireAccess} rather than {@code requireDesign} — unlike
 * {@link FindAppDefinition} this is a run-time read, so every member of the organization may make it.
 * The routes it returns are not role-filtered here; the sidenav that leads to them already is, by
 * {@link GetAppLayout}.
 */
@Service
@Transactional(readOnly = true)
public class FindModuleDefinition {

    private final ModuleDefinitionRepository repository;
    private final OrganizationGuard guard;

    public FindModuleDefinition(ModuleDefinitionRepository repository, OrganizationGuard guard) {
        this.repository = repository;
        this.guard = guard;
    }

    public ModuleDefinition execute(String orgKey, String moduleKey) {
        guard.requireAccess(orgKey);
        return repository.findByOrgKeyAndKey(orgKey, moduleKey)
                .orElseThrow(() -> new ModuleDefinitionNotFoundException(orgKey, moduleKey));
    }
}
