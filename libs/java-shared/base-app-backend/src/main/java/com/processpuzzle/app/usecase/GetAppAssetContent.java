package com.processpuzzle.app.usecase;

import com.processpuzzle.app.domain.AppAsset;
import com.processpuzzle.app.domain.AppAssetRepository;
import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.app.usecase.exception.AppAssetNotFoundException;
import com.processpuzzle.app.usecase.exception.AppDefinitionNotFoundException;
import com.processpuzzle.app.usecase.exception.ModuleAssetStoreUnavailableException;
import com.processpuzzle.app.usecase.port.ModuleAssetStore;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One asset of an app with its bytes. {@link #summaryOf} answers without them, for a caller that already holds
 * the content (a conditional request) and only needs the hash to compare.
 */
@Service
@Transactional(readOnly = true)
public class GetAppAssetContent {

    private final AppDefinitionRepository apps;
    private final AppAssetRepository assets;
    private final ObjectProvider<ModuleAssetStore> store;
    private final OrganizationGuard guard;

    public GetAppAssetContent(AppDefinitionRepository apps, AppAssetRepository assets, ObjectProvider<ModuleAssetStore> store,
                              OrganizationGuard guard) {
        this.apps = apps;
        this.assets = assets;
        this.store = store;
        this.guard = guard;
    }

    public ModuleAssetSummary summaryOf(String orgKey, String appId, String path, Channel channel) {
        AppAsset asset = record(orgKey, appId, path, channel);
        return new ModuleAssetSummary(asset.getPath(), asset.getMimeType(), asset.getSize(), asset.getSha256());
    }

    public ModuleAssetContent execute(String orgKey, String appId, String path, Channel channel) {
        AppAsset asset = record(orgKey, appId, path, channel);
        ModuleAssetStore objects = store.getIfAvailable();
        if (objects == null) {
            throw new ModuleAssetStoreUnavailableException("No object store is configured for app assets.");
        }
        byte[] content = objects.get(orgKey, asset.getSha256())
                .orElseThrow(() -> new AppAssetNotFoundException(orgKey, appId, path));
        return new ModuleAssetContent(asset.getPath(), asset.getMimeType(), asset.getSha256(), content);
    }

    private AppAsset record(String orgKey, String appId, String path, Channel channel) {
        FindAllModuleDefinitions.requireReader(guard, orgKey, channel);
        if (!apps.existsByOrgKeyAndId(orgKey, appId)) {
            throw new AppDefinitionNotFoundException(orgKey, appId);
        }
        return assets.findByOrgKeyAndAppIdAndChannelAndPath(orgKey, appId, channel, path)
                .orElseThrow(() -> new AppAssetNotFoundException(orgKey, appId, path));
    }
}
