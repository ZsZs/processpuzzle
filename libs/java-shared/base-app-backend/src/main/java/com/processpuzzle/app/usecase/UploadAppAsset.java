package com.processpuzzle.app.usecase;

import com.processpuzzle.app.domain.AppAsset;
import com.processpuzzle.app.domain.AppAssetRepository;
import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.app.usecase.exception.AppDefinitionNotFoundException;
import com.processpuzzle.app.usecase.exception.ModuleAssetRejectedException;
import com.processpuzzle.app.usecase.exception.ModuleAssetStoreUnavailableException;
import com.processpuzzle.app.usecase.port.ModuleAssetStore;
import com.processpuzzle.app.usecase.service.ModuleAssetPaths;
import com.processpuzzle.app.usecase.service.ModuleAssetSweeper;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adds or replaces one asset of an app's draft: {@link UploadModuleAsset} one level up, with the same limits,
 * path rules and object store. Replacing a path with other content leaves the old bytes to the sweeper.
 */
@Service
@Transactional
public class UploadAppAsset {

    /** Per app and channel. */
    public static final int MAX_ASSETS = 500;

    private final AppDefinitionRepository apps;
    private final AppAssetRepository assets;
    private final ObjectProvider<ModuleAssetStore> store;
    private final ModuleAssetSweeper sweeper;
    private final OrganizationGuard guard;

    public UploadAppAsset(AppDefinitionRepository apps, AppAssetRepository assets, ObjectProvider<ModuleAssetStore> store,
                          ModuleAssetSweeper sweeper, OrganizationGuard guard) {
        this.apps = apps;
        this.assets = assets;
        this.store = store;
        this.sweeper = sweeper;
        this.guard = guard;
    }

    /**
     * @param path where in the app the file goes; the file's own name, at the root, when blank
     * @param fileName the uploaded file's name, used for the default path and to guess a missing content type
     */
    public ModuleAssetSummary execute(String orgKey, String appId, String path, String fileName,
                                      String contentType, byte[] content) {
        guard.requireDesign(orgKey);
        if (!apps.existsByOrgKeyAndId(orgKey, appId)) {
            throw new AppDefinitionNotFoundException(orgKey, appId);
        }
        String target = ModuleAssetPaths.require(path == null || path.isBlank() ? ModuleAssetPaths.fileNameOf(fileName) : path);
        if (content.length == 0) {
            throw new ModuleAssetRejectedException("'" + target + "' is empty.");
        }
        if (content.length > UploadModuleAsset.MAX_BYTES) {
            throw new ModuleAssetRejectedException("'" + target + "' is larger than " + UploadModuleAsset.MAX_BYTES + " bytes.");
        }
        ModuleAssetStore objects = store.getIfAvailable();
        if (objects == null) {
            throw new ModuleAssetStoreUnavailableException("No object store is configured for app assets.");
        }
        Optional<AppAsset> existing = assets.findByOrgKeyAndAppIdAndChannelAndPath(orgKey, appId, Channel.DRAFT, target);
        if (existing.isEmpty() && assets.countByOrgKeyAndAppIdAndChannel(orgKey, appId, Channel.DRAFT) >= MAX_ASSETS) {
            throw new ModuleAssetRejectedException("An app holds at most " + MAX_ASSETS + " assets.");
        }
        String sha256 = UploadModuleAsset.sha256Of(content);
        String mimeType = UploadModuleAsset.mimeTypeOf(contentType, target);
        objects.put(orgKey, sha256, content, mimeType);
        AppAsset asset = existing.orElseGet(() -> new AppAsset(orgKey, appId, Channel.DRAFT, target, sha256, mimeType, content.length));
        String replaced = existing.map(AppAsset::getSha256).orElse(null);
        asset.replaceContent(sha256, mimeType, content.length);
        assets.save(asset);
        if (replaced != null && !replaced.equals(sha256)) {
            sweeper.sweep(orgKey, List.of(replaced));
        }
        return new ModuleAssetSummary(target, mimeType, content.length, sha256);
    }
}
