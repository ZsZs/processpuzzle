package com.processpuzzle.app.usecase;

import com.processpuzzle.app.domain.AppAsset;
import com.processpuzzle.app.domain.AppAssetRepository;
import com.processpuzzle.app.usecase.exception.AppAssetNotFoundException;
import com.processpuzzle.app.usecase.service.ModuleAssetSweeper;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Removes one asset from the app's draft. The published app keeps its own record — and so its bytes, which the
 * sweeper leaves alone while any record names them — until the next publish.
 */
@Service
@Transactional
public class DeleteAppAsset {

    private final AppAssetRepository assets;
    private final ModuleAssetSweeper sweeper;
    private final OrganizationGuard guard;

    public DeleteAppAsset(AppAssetRepository assets, ModuleAssetSweeper sweeper, OrganizationGuard guard) {
        this.assets = assets;
        this.sweeper = sweeper;
        this.guard = guard;
    }

    public void execute(String orgKey, String appId, String path) {
        guard.requireDesign(orgKey);
        AppAsset asset = assets.findByOrgKeyAndAppIdAndChannelAndPath(orgKey, appId, Channel.DRAFT, path)
                .orElseThrow(() -> new AppAssetNotFoundException(orgKey, appId, path));
        assets.delete(asset);
        sweeper.sweep(orgKey, List.of(asset.getSha256()));
    }
}
