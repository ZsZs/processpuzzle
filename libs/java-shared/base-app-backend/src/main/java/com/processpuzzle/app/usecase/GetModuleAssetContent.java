package com.processpuzzle.app.usecase;

import com.processpuzzle.app.domain.ModuleAsset;
import com.processpuzzle.app.domain.ModuleAssetRepository;
import com.processpuzzle.app.usecase.exception.ModuleAssetNotFoundException;
import com.processpuzzle.app.usecase.exception.ModuleAssetStoreUnavailableException;
import com.processpuzzle.app.usecase.exception.ModuleDefinitionNotFoundException;
import com.processpuzzle.app.domain.ModuleDefinitionRepository;
import com.processpuzzle.app.usecase.port.ModuleAssetStore;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One asset with its bytes. {@link #find} answers without them, for a caller that already holds the content
 * (a conditional request) and only needs the hash to compare.
 */
@Service
@Transactional(readOnly = true)
public class GetModuleAssetContent {

    private final ModuleDefinitionRepository modules;
    private final ModuleAssetRepository assets;
    private final ObjectProvider<ModuleAssetStore> store;
    private final OrganizationGuard guard;

    public GetModuleAssetContent(ModuleDefinitionRepository modules, ModuleAssetRepository assets,
                                 ObjectProvider<ModuleAssetStore> store, OrganizationGuard guard) {
        this.modules = modules;
        this.assets = assets;
        this.store = store;
        this.guard = guard;
    }

    public ModuleAssetSummary find(String orgKey, String moduleKey, String path, Channel channel) {
        ModuleAsset asset = record(orgKey, moduleKey, path, channel);
        return new ModuleAssetSummary(asset.getPath(), asset.getMimeType(), asset.getSize(), asset.getSha256());
    }

    public ModuleAssetContent execute(String orgKey, String moduleKey, String path, Channel channel) {
        ModuleAsset asset = record(orgKey, moduleKey, path, channel);
        ModuleAssetStore objects = store.getIfAvailable();
        if (objects == null) {
            throw new ModuleAssetStoreUnavailableException("No object store is configured for module assets.");
        }
        byte[] content = objects.get(orgKey, asset.getSha256())
                .orElseThrow(() -> new ModuleAssetNotFoundException(orgKey, moduleKey, path));
        return new ModuleAssetContent(asset.getPath(), asset.getMimeType(), asset.getSha256(), content);
    }

    private ModuleAsset record(String orgKey, String moduleKey, String path, Channel channel) {
        FindAllModuleDefinitions.requireReader(guard, orgKey, channel);
        if (!modules.existsByOrgKeyAndKeyAndChannel(orgKey, moduleKey, channel)) {
            throw new ModuleDefinitionNotFoundException(orgKey, moduleKey);
        }
        return assets.findByOrgKeyAndModuleKeyAndChannelAndPath(orgKey, moduleKey, channel, path)
                .orElseThrow(() -> new ModuleAssetNotFoundException(orgKey, moduleKey, path));
    }
}
