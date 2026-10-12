package com.processpuzzle.app.usecase;

import com.processpuzzle.app.domain.ModuleDefinition;
import com.processpuzzle.app.domain.ModuleDefinitionRepository;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The authoring list. Unpaged on purpose: modules are a handful per organization by construction —
 * they exist to carve one application into a few slices, not to hold data.
 */
@Service
@Transactional(readOnly = true)
public class FindAllModuleDefinitions {

    private final ModuleDefinitionRepository repository;
    private final OrganizationGuard guard;

    public FindAllModuleDefinitions(ModuleDefinitionRepository repository, OrganizationGuard guard) {
        this.repository = repository;
        this.guard = guard;
    }

    public List<ModuleDefinition> execute(String orgKey) {
        return execute(orgKey, Channel.DRAFT);
    }

    /** The drafts require design rights, as every authoring read does; the released rows only membership. */
    public List<ModuleDefinition> execute(String orgKey, Channel channel) {
        requireReader(guard, orgKey, channel);
        return repository.findByOrgKeyAndChannel(orgKey, channel);
    }

    static void requireReader(OrganizationGuard guard, String orgKey, Channel channel) {
        if (channel == Channel.RELEASED) {
            guard.requireAccess(orgKey);
        } else {
            guard.requireDesign(orgKey);
        }
    }
}
