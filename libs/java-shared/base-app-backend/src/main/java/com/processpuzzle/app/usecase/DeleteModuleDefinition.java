package com.processpuzzle.app.usecase;

import com.processpuzzle.app.domain.ModuleDefinition;
import com.processpuzzle.app.domain.ModuleAsset;
import com.processpuzzle.app.domain.ModuleAssetRepository;
import com.processpuzzle.app.domain.ModuleDefinitionRepository;
import com.processpuzzle.app.domain.ModuleTranslationRepository;
import com.processpuzzle.app.usecase.exception.ModuleDefinitionNotFoundException;
import com.processpuzzle.app.usecase.service.ModuleAssetSweeper;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes a module. Deliberately does not cascade into the apps that mount it: a mount naming a
 * module that no longer exists stays put and degrades to a validation warning, the same loose
 * coupling that lets an app mount a module before it has been authored.
 *
 * <p>Deletes the draft only, with its draft translations and assets. The released module stays live until the
 * organization's next release, which removes it.
 */
@Service
@Transactional
public class DeleteModuleDefinition {

    private final ModuleDefinitionRepository repository;
    private final ModuleTranslationRepository translations;
    private final ModuleAssetRepository assets;
    private final ModuleAssetSweeper sweeper;
    private final OrganizationGuard guard;

    public DeleteModuleDefinition(ModuleDefinitionRepository repository, ModuleTranslationRepository translations,
                                  ModuleAssetRepository assets, ModuleAssetSweeper sweeper, OrganizationGuard guard) {
        this.assets = assets;
        this.sweeper = sweeper;
        this.repository = repository;
        this.translations = translations;
        this.guard = guard;
    }

    public void execute(String orgKey, String moduleKey) {
        guard.requireDesign(orgKey);
        ModuleDefinition existing = repository.findByOrgKeyAndKeyAndChannel(orgKey, moduleKey, Channel.DRAFT)
                .orElseThrow(() -> new ModuleDefinitionNotFoundException(orgKey, moduleKey));
        repository.delete(existing);
        translations.deleteByOrgKeyAndModuleKeyAndChannel(orgKey, moduleKey, Channel.DRAFT);
        List<ModuleAsset> draftAssets = assets.findByOrgKeyAndModuleKeyAndChannelOrderByPath(orgKey, moduleKey, Channel.DRAFT);
        assets.deleteAll(draftAssets);
        sweeper.sweep(orgKey, draftAssets.stream().map(ModuleAsset::getSha256).distinct().toList());
    }
}
