package com.processpuzzle.app.usecase.service;

import com.processpuzzle.app.domain.AppAsset;
import com.processpuzzle.app.domain.AppAssetRepository;
import com.processpuzzle.core.definition.Channel;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Publishes the assets of an app together with the app, as {@code ModuleReleaseParticipant} does for a module.
 * What is copied is the <em>records</em> — path, content hash, type — never the files: a released and a draft
 * record of one path name the same object until the draft is replaced. The hashes a released record stops
 * naming are collected for the caller to hand to the {@link ModuleAssetSweeper}.
 */
@Component
public class AppAssetReleases {

    private final AppAssetRepository assets;

    public AppAssetReleases(AppAssetRepository assets) {
        this.assets = assets;
    }

    /** Makes the released records of the app equal to its draft ones. */
    public void release(String orgKey, String appId, Set<String> droppedHashes) {
        Map<String, AppAsset> released = assets.findByOrgKeyAndAppIdAndChannelOrderByPath(orgKey, appId, Channel.RELEASED).stream()
                .collect(Collectors.toMap(AppAsset::getPath, Function.identity()));
        Set<String> kept = new TreeSet<>();
        for (AppAsset draft : assets.findByOrgKeyAndAppIdAndChannelOrderByPath(orgKey, appId, Channel.DRAFT)) {
            kept.add(draft.getPath());
            AppAsset current = released.get(draft.getPath());
            if (current == null) {
                assets.save(new AppAsset(orgKey, appId, Channel.RELEASED, draft.getPath(), draft.getSha256(),
                        draft.getMimeType(), draft.getSize()));
            } else {
                if (!current.getSha256().equals(draft.getSha256())) {
                    droppedHashes.add(current.getSha256());
                }
                current.replaceContent(draft.getSha256(), draft.getMimeType(), draft.getSize());
            }
        }
        released.forEach((path, current) -> {
            if (!kept.contains(path)) {
                droppedHashes.add(current.getSha256());
                assets.delete(current);
            }
        });
    }

    /** Deletes the records of the app in both channels, for deleting the app; the hashes they named go to {@code droppedHashes}. */
    public void removeAll(String orgKey, String appId, Collection<String> droppedHashes) {
        var all = assets.findByOrgKeyAndAppId(orgKey, appId);
        all.forEach(asset -> droppedHashes.add(asset.getSha256()));
        assets.deleteAll(all);
    }

    /** App id to path to "hash:type", the paths sorted so that equal content fingerprints equally. */
    public Map<String, Map<String, String>> byApp(String orgKey, Channel channel) {
        Map<String, Map<String, String>> byApp = new LinkedHashMap<>();
        for (AppAsset asset : assets.findByOrgKeyAndChannel(orgKey, channel)) {
            byApp.computeIfAbsent(asset.getAppId(), id -> new TreeMap<>())
                    .put(asset.getPath(), asset.getSha256() + ":" + asset.getMimeType());
        }
        return byApp;
    }
}
