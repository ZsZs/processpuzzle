package com.processpuzzle.app.adapter.inbound;

import com.processpuzzle.app.domain.AppAsset;
import com.processpuzzle.app.domain.AppAssetRepository;
import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.app.domain.ModuleAsset;
import com.processpuzzle.app.domain.ModuleAssetRepository;
import com.processpuzzle.app.domain.ModuleDefinitionRepository;
import com.processpuzzle.app.usecase.UploadAppAsset;
import com.processpuzzle.app.usecase.UploadModuleAsset;
import com.processpuzzle.app.usecase.service.AppAssetReleases;
import com.processpuzzle.app.usecase.service.ModuleAssetSweeper;
import com.processpuzzle.core.definition.CanonicalJson;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.definition.DefinitionImportParticipant;
import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.ImportedItem;
import com.processpuzzle.core.definition.ParticipantResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

/**
 * Imports the files a Business Starter's modules and apps bring along — {@code assets/modules/<key>/<path>} and
 * {@code assets/apps/<id>/<path>}, see {@link BundleOwnedPath} — through {@link UploadModuleAsset} and
 * {@link UploadAppAsset}, so a file meets the same path, size and count rules as one uploaded in the designer. The key
 * is the path without its first segment.
 *
 * <p>Runs after the apps, which are published when they are imported, so each file is released here, right after it
 * is stored: the released records are what the running application serves, and a module's and an app's released
 * records are copied from the drafts the same way a designer's Publish does it. A file whose module or app the
 * bundle does not define is refused.
 *
 * <p>The importer removes everything before it applies, so a file that disappeared from the bundle is gone after a
 * reinstall; {@link #removeAll} does that for the assets themselves. The fingerprint of a file is its content hash and
 * type, so a replaced file is a customized one.
 */
@Component
public class AssetImportParticipant implements DefinitionImportParticipant {

    private final UploadModuleAsset uploadModuleAsset;
    private final UploadAppAsset uploadAppAsset;
    private final ModuleAssetRepository moduleAssets;
    private final AppAssetRepository appAssets;
    private final ModuleDefinitionRepository modules;
    private final AppDefinitionRepository apps;
    private final ModuleReleaseParticipant moduleRelease;
    private final AppAssetReleases appRelease;
    private final ModuleAssetSweeper sweeper;

    public AssetImportParticipant(UploadModuleAsset uploadModuleAsset, UploadAppAsset uploadAppAsset,
                                  ModuleAssetRepository moduleAssets, AppAssetRepository appAssets,
                                  ModuleDefinitionRepository modules, AppDefinitionRepository apps,
                                  ModuleReleaseParticipant moduleRelease, AppAssetReleases appRelease,
                                  ModuleAssetSweeper sweeper) {
        this.uploadModuleAsset = uploadModuleAsset;
        this.uploadAppAsset = uploadAppAsset;
        this.moduleAssets = moduleAssets;
        this.appAssets = appAssets;
        this.modules = modules;
        this.apps = apps;
        this.moduleRelease = moduleRelease;
        this.appRelease = appRelease;
        this.sweeper = sweeper;
    }

    @Override
    public String kind() {
        return DefinitionKinds.ASSET;
    }

    @Override
    public int order() {
        return DefinitionKinds.ASSET_ORDER;
    }

    @Override
    public ParticipantResult apply(String orgKey, String fileName, byte[] content) {
        BundleOwnedPath path = BundleOwnedPath.parse(fileName).orElse(null);
        if (path == null) {
            return ParticipantResult.rejected(List.of(fileName + ": an asset belongs under <group>/modules/<module key>/ or "
                    + "<group>/apps/<app id>/, followed by its path."));
        }
        boolean existed = existing(orgKey, path);
        try {
            if (path.isApp()) {
                if (!apps.existsByOrgKeyAndId(orgKey, path.ownerKey())) {
                    return ParticipantResult.rejected(List.of(fileName + ": the bundle defines no app '" + path.ownerKey() + "'."));
                }
                uploadAppAsset.execute(orgKey, path.ownerKey(), path.rest(), path.fileName(), null, content);
                Set<String> dropped = new TreeSet<>();
                appRelease.release(orgKey, path.ownerKey(), dropped);
                sweeper.sweep(orgKey, dropped);
            } else {
                if (!modules.existsByOrgKeyAndKeyAndChannel(orgKey, path.ownerKey(), Channel.DRAFT)) {
                    return ParticipantResult.rejected(List.of(fileName + ": the bundle defines no module '" + path.ownerKey() + "'."));
                }
                uploadModuleAsset.execute(orgKey, path.ownerKey(), path.rest(), path.fileName(), null, content);
                moduleRelease.release(orgKey);
            }
        } catch (RuntimeException e) {
            return ParticipantResult.rejected(List.of(fileName + ": " + e.getMessage()));
        }
        return new ParticipantResult(List.of(new ImportedItem(path.key(),
                existed ? ImportedItem.Action.UPDATE : ImportedItem.Action.CREATE)), List.of());
    }

    /**
     * Deletes every asset of every module and app, in both channels, and hands the content they named to the sweeper.
     * Flushed, because Hibernate runs inserts before deletes and the import that follows reuses the paths.
     */
    @Override
    public List<String> removeAll(String orgKey) {
        List<String> keys = new ArrayList<>(keysOf(orgKey).keySet());
        Set<String> hashes = new TreeSet<>();
        for (Channel channel : Channel.values()) {
            List<ModuleAsset> moduleRows = moduleAssets.findByOrgKeyAndChannel(orgKey, channel);
            moduleRows.forEach(asset -> hashes.add(asset.getSha256()));
            moduleAssets.deleteAll(moduleRows);
            List<AppAsset> appRows = appAssets.findByOrgKeyAndChannel(orgKey, channel);
            appRows.forEach(asset -> hashes.add(asset.getSha256()));
            appAssets.deleteAll(appRows);
        }
        moduleAssets.flush();
        sweeper.sweep(orgKey, hashes);
        return keys;
    }

    @Override
    public Map<String, String> fingerprints(String orgKey, Set<String> keys) {
        Map<String, String> fingerprints = new LinkedHashMap<>();
        keysOf(orgKey).forEach((key, content) -> {
            if (keys.contains(key)) {
                fingerprints.put(key, content);
            }
        });
        return fingerprints;
    }

    /** Each draft asset's key and the canonical form of what it holds: the hash and the type, never the bytes. */
    private Map<String, String> keysOf(String orgKey) {
        Map<String, String> byKey = new LinkedHashMap<>();
        for (AppAsset asset : appAssets.findByOrgKeyAndChannel(orgKey, Channel.DRAFT)) {
            byKey.put(BundleOwnedPath.APPS + "/" + asset.getAppId() + "/" + asset.getPath(),
                    fingerprint(asset.getSha256(), asset.getMimeType()));
        }
        for (ModuleAsset asset : moduleAssets.findByOrgKeyAndChannel(orgKey, Channel.DRAFT)) {
            byKey.put(BundleOwnedPath.MODULES + "/" + asset.getModuleKey() + "/" + asset.getPath(),
                    fingerprint(asset.getSha256(), asset.getMimeType()));
        }
        return byKey;
    }

    private boolean existing(String orgKey, BundleOwnedPath path) {
        return path.isApp()
                ? appAssets.findByOrgKeyAndAppIdAndChannelAndPath(orgKey, path.ownerKey(), Channel.DRAFT, path.rest()).isPresent()
                : moduleAssets.findByOrgKeyAndModuleKeyAndChannelAndPath(orgKey, path.ownerKey(), Channel.DRAFT, path.rest()).isPresent();
    }

    private static String fingerprint(String sha256, String mimeType) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("sha256", sha256);
        content.put("mimeType", mimeType);
        return CanonicalJson.of(content, Set.of());
    }
}
