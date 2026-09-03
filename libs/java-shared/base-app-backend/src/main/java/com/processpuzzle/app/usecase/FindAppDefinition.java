package com.processpuzzle.app.usecase;

import com.processpuzzle.app.domain.AppDefinition;
import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.app.usecase.exception.AppDefinitionNotFoundException;
import com.processpuzzle.platformadmin.usecase.OrganizationGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the complete definition graph, unfiltered by role — the authoring view. The run-time shell
 * uses {@link GetAppLayout} plus {@link GetRouteDefinition} instead.
 */
@Service
@Transactional(readOnly = true)
public class FindAppDefinition {

    private final AppDefinitionRepository repository;
    private final OrganizationGuard guard;

    public FindAppDefinition(AppDefinitionRepository repository, OrganizationGuard guard) {
        this.repository = repository;
        this.guard = guard;
    }

    public AppDefinition execute(String orgKey, String appId) {
        guard.requireDesign(orgKey);
        return repository.findByOrgKeyAndId(orgKey, appId)
                .orElseThrow(() -> new AppDefinitionNotFoundException(orgKey, appId));
    }
}
