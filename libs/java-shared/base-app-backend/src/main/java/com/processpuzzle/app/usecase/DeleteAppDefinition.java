package com.processpuzzle.app.usecase;

import com.processpuzzle.app.domain.AppDefinition;
import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.app.usecase.exception.AppDefinitionNotFoundException;
import com.processpuzzle.app.usecase.service.AppAssetReleases;
import com.processpuzzle.app.usecase.service.AppTranslationReleases;
import com.processpuzzle.app.usecase.service.ModuleAssetSweeper;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes both graphs of the app, the records of its assets, whose content the sweeper then removes if nothing else
 * names it, and its translations.
 */
@Service
@Transactional
public class DeleteAppDefinition {

    private final AppDefinitionRepository repository;
    private final AppAssetReleases assets;
    private final AppTranslationReleases translations;
    private final ModuleAssetSweeper sweeper;
    private final OrganizationGuard guard;

    public DeleteAppDefinition(AppDefinitionRepository repository, AppAssetReleases assets,
                               AppTranslationReleases translations, ModuleAssetSweeper sweeper, OrganizationGuard guard) {
        this.repository = repository;
        this.assets = assets;
        this.translations = translations;
        this.sweeper = sweeper;
        this.guard = guard;
    }

    public void execute(String orgKey, String appId) {
        guard.requireDesign(orgKey);
        AppDefinition existing = repository.findByOrgKeyAndId(orgKey, appId)
                .orElseThrow(() -> new AppDefinitionNotFoundException(orgKey, appId));
        repository.delete(existing);
        Set<String> dropped = new TreeSet<>();
        assets.removeAll(orgKey, appId, dropped);
        translations.removeAll(orgKey, appId);
        sweeper.sweep(orgKey, dropped);
    }
}
