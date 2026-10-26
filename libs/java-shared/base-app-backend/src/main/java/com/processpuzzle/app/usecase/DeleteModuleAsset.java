package com.processpuzzle.app.usecase;

import com.processpuzzle.app.domain.ModuleAsset;
import com.processpuzzle.app.domain.ModuleAssetRepository;
import com.processpuzzle.app.usecase.exception.ModuleAssetNotFoundException;
import com.processpuzzle.app.usecase.service.ModuleAssetSweeper;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Removes one asset from the module draft. The released module keeps its own record — and so its bytes, which
 * the sweeper leaves alone while any record names them — until the next release.
 */
@Service
@Transactional
public class DeleteModuleAsset {

    private final ModuleAssetRepository assets;
    private final ModuleAssetSweeper sweeper;
    private final OrganizationGuard guard;

    public DeleteModuleAsset(ModuleAssetRepository assets, ModuleAssetSweeper sweeper, OrganizationGuard guard) {
        this.assets = assets;
        this.sweeper = sweeper;
        this.guard = guard;
    }

    public void execute(String orgKey, String moduleKey, String path) {
        guard.requireDesign(orgKey);
        ModuleAsset asset = assets.findByOrgKeyAndModuleKeyAndChannelAndPath(orgKey, moduleKey, Channel.DRAFT, path)
                .orElseThrow(() -> new ModuleAssetNotFoundException(orgKey, moduleKey, path));
        assets.delete(asset);
        sweeper.sweep(orgKey, List.of(asset.getSha256()));
    }
}
