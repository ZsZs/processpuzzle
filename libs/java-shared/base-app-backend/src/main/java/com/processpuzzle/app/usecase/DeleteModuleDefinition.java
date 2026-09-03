package com.processpuzzle.app.usecase;

import com.processpuzzle.app.domain.ModuleDefinition;
import com.processpuzzle.app.domain.ModuleDefinitionRepository;
import com.processpuzzle.app.usecase.exception.ModuleDefinitionNotFoundException;
import com.processpuzzle.platformadmin.usecase.OrganizationGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes a module. Deliberately does not cascade into the apps that mount it: a mount naming a
 * module that no longer exists stays put and degrades to a validation warning, the same loose
 * coupling that lets an app mount a module before it has been authored.
 */
@Service
@Transactional
public class DeleteModuleDefinition {

    private final ModuleDefinitionRepository repository;
    private final OrganizationGuard guard;

    public DeleteModuleDefinition(ModuleDefinitionRepository repository, OrganizationGuard guard) {
        this.repository = repository;
        this.guard = guard;
    }

    public void execute(String orgKey, String moduleKey) {
        guard.requireDesign(orgKey);
        ModuleDefinition existing = repository.findByOrgKeyAndKey(orgKey, moduleKey)
                .orElseThrow(() -> new ModuleDefinitionNotFoundException(orgKey, moduleKey));
        repository.delete(existing);
    }
}
