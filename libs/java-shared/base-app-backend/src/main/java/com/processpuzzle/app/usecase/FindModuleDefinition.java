package com.processpuzzle.app.usecase;

import com.processpuzzle.app.domain.ModuleDefinition;
import com.processpuzzle.app.domain.ModuleDefinitionRepository;
import com.processpuzzle.app.usecase.exception.ModuleDefinitionNotFoundException;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One module, from either channel. The draft is the designer's read and requires design rights. The
 * released row is a run-time read every member of the organization may make; its routes are not
 * role-filtered here, the sidenav that leads to them already is, by {@link GetAppLayout} — which also
 * carries the released modules inline, so the runtime host has no need of this read.
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
        return execute(orgKey, moduleKey, Channel.DRAFT);
    }

    public ModuleDefinition execute(String orgKey, String moduleKey, Channel channel) {
        FindAllModuleDefinitions.requireReader(guard, orgKey, channel);
        return repository.findByOrgKeyAndKeyAndChannel(orgKey, moduleKey, channel)
                .orElseThrow(() -> new ModuleDefinitionNotFoundException(orgKey, moduleKey));
    }
}
