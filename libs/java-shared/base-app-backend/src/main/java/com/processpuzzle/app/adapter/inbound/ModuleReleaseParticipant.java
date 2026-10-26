package com.processpuzzle.app.adapter.inbound;

import com.processpuzzle.app.domain.ModuleAsset;
import com.processpuzzle.app.domain.ModuleAssetRepository;
import com.processpuzzle.app.domain.ModuleDefinition;
import com.processpuzzle.app.domain.ModuleDefinitionRepository;
import com.processpuzzle.app.domain.ModuleTranslation;
import com.processpuzzle.app.domain.ModuleTranslationRepository;
import com.processpuzzle.app.usecase.service.ModuleAssetSweeper;
import com.processpuzzle.core.definition.CanonicalJson;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.DefinitionReference;
import com.processpuzzle.core.definition.PendingChange;
import com.processpuzzle.core.definition.ReleaseCandidate;
import com.processpuzzle.core.definition.ReleaseParticipant;
import com.processpuzzle.core.definition.ReleaseProblem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Releases modules: copies each draft onto its released row, and deletes the released rows whose
 * draft was deleted. A module's drafts were validated when saved, so there is nothing more to check.
 *
 * <p>A module's uploaded translations travel with it: they are part of what is pending, and release
 * copies them onto the released channel, language by language, so that what the running application loads
 * is always the translations that were released together with the routes they name.
 *
 * <p>So do its assets, and for the same reason. What is copied is the <em>records</em> — path, content hash,
 * type — never the files: a released and a draft record of one path name the same object until the draft is
 * replaced, and the sweeper removes an object once no record names it.
 */
@Component
public class ModuleReleaseParticipant implements ReleaseParticipant {

    private final ModuleDefinitionRepository repository;
    private final ModuleTranslationRepository translations;
    private final ModuleAssetRepository assets;
    private final ModuleAssetSweeper sweeper;

    public ModuleReleaseParticipant(ModuleDefinitionRepository repository, ModuleTranslationRepository translations,
                                    ModuleAssetRepository assets, ModuleAssetSweeper sweeper) {
        this.repository = repository;
        this.translations = translations;
        this.assets = assets;
        this.sweeper = sweeper;
    }

    @Override
    public String kind() {
        return DefinitionKinds.MODULE;
    }

    @Override
    public int order() {
        return DefinitionKinds.MODULE_ORDER;
    }

    @Override
    public Set<String> drafts(String orgKey) {
        return byKey(orgKey, Channel.DRAFT).keySet();
    }

    @Override
    public List<PendingChange> pending(String orgKey) {
        Map<String, ModuleDefinition> drafts = byKey(orgKey, Channel.DRAFT);
        Map<String, ModuleDefinition> released = byKey(orgKey, Channel.RELEASED);
        Set<String> keys = new TreeSet<>(drafts.keySet());
        keys.addAll(released.keySet());
        Map<String, Map<String, Map<String, String>>> draftTranslations = translationsByModule(orgKey, Channel.DRAFT);
        Map<String, Map<String, Map<String, String>>> releasedTranslations = translationsByModule(orgKey, Channel.RELEASED);
        Map<String, Map<String, String>> draftAssets = assetsByModule(orgKey, Channel.DRAFT);
        Map<String, Map<String, String>> releasedAssets = assetsByModule(orgKey, Channel.RELEASED);

        List<PendingChange> pending = new ArrayList<>();
        for (String key : keys) {
            PendingChange change = PendingChange.between(kind(), key,
                    fingerprint(drafts.get(key), draftTranslations.get(key), draftAssets.get(key)),
                    fingerprint(released.get(key), releasedTranslations.get(key), releasedAssets.get(key)));
            if (change != null) {
                pending.add(change);
            }
        }
        return pending;
    }

    @Override
    public List<DefinitionReference> references(String orgKey) {
        return repository.findByOrgKeyAndChannel(orgKey, Channel.DRAFT).stream()
                .flatMap(module -> RouteReferences.of(kind(), module.getKey(), module.getRoutes()).stream())
                .toList();
    }

    @Override
    public List<ReleaseProblem> check(String orgKey, ReleaseCandidate candidate) {
        return List.of();
    }

    @Override
    public void release(String orgKey) {
        Map<String, ModuleDefinition> drafts = byKey(orgKey, Channel.DRAFT);
        Map<String, ModuleDefinition> released = byKey(orgKey, Channel.RELEASED);
        Set<String> droppedHashes = new TreeSet<>();
        drafts.forEach((key, draft) -> {
            ModuleDefinition current = released.get(key);
            if (current == null) {
                repository.save(ModuleDefinition.releasedCopyOf(draft));
            } else {
                current.releaseFrom(draft);
            }
            releaseTranslations(orgKey, key);
            releaseAssets(orgKey, key, droppedHashes);
        });
        released.forEach((key, current) -> {
            if (!drafts.containsKey(key)) {
                repository.delete(current);
                translations.deleteByOrgKeyAndModuleKeyAndChannel(orgKey, key, Channel.RELEASED);
                List<ModuleAsset> gone = assets.findByOrgKeyAndModuleKeyAndChannelOrderByPath(orgKey, key, Channel.RELEASED);
                gone.forEach(asset -> droppedHashes.add(asset.getSha256()));
                assets.deleteAll(gone);
            }
        });
        sweeper.sweep(orgKey, droppedHashes);
    }

    /**
     * Records only; the content is shared. The hashes a released record no longer names are handed to the
     * sweeper, which removes the object if the draft does not name it either.
     */
    private void releaseAssets(String orgKey, String moduleKey, Set<String> droppedHashes) {
        Map<String, ModuleAsset> released = assets
                .findByOrgKeyAndModuleKeyAndChannelOrderByPath(orgKey, moduleKey, Channel.RELEASED).stream()
                .collect(Collectors.toMap(ModuleAsset::getPath, Function.identity()));
        Set<String> kept = new TreeSet<>();
        for (ModuleAsset draft : assets.findByOrgKeyAndModuleKeyAndChannelOrderByPath(orgKey, moduleKey, Channel.DRAFT)) {
            kept.add(draft.getPath());
            ModuleAsset current = released.get(draft.getPath());
            if (current == null) {
                assets.save(new ModuleAsset(orgKey, moduleKey, Channel.RELEASED, draft.getPath(), draft.getSha256(),
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

    /** Updates in place rather than deleting and re-inserting: Hibernate runs inserts before deletes, which would collide on the key. */
    private void releaseTranslations(String orgKey, String moduleKey) {
        Map<String, ModuleTranslation> released = translations
                .findByOrgKeyAndModuleKeyAndChannel(orgKey, moduleKey, Channel.RELEASED).stream()
                .collect(Collectors.toMap(ModuleTranslation::getLocale, Function.identity()));
        Set<String> kept = new TreeSet<>();
        for (ModuleTranslation draft : translations.findByOrgKeyAndModuleKeyAndChannel(orgKey, moduleKey, Channel.DRAFT)) {
            kept.add(draft.getLocale());
            ModuleTranslation current = released.get(draft.getLocale());
            if (current == null) {
                translations.save(new ModuleTranslation(orgKey, moduleKey, Channel.RELEASED, draft.getLocale(),
                        draft.getMessages()));
            } else {
                current.setMessages(draft.getMessages());
            }
        }
        released.forEach((locale, current) -> {
            if (!kept.contains(locale)) {
                translations.delete(current);
            }
        });
    }

    /** Module key to path to "hash:type", the paths sorted so that equal content fingerprints equally. */
    private Map<String, Map<String, String>> assetsByModule(String orgKey, Channel channel) {
        Map<String, Map<String, String>> byModule = new LinkedHashMap<>();
        for (ModuleAsset asset : assets.findByOrgKeyAndChannel(orgKey, channel)) {
            byModule.computeIfAbsent(asset.getModuleKey(), key -> new TreeMap<>())
                    .put(asset.getPath(), asset.getSha256() + ":" + asset.getMimeType());
        }
        return byModule;
    }

    /** Module key to language to messages, the languages sorted so that equal content fingerprints equally. */
    private Map<String, Map<String, Map<String, String>>> translationsByModule(String orgKey, Channel channel) {
        Map<String, Map<String, Map<String, String>>> byModule = new LinkedHashMap<>();
        for (ModuleTranslation translation : translations.findByOrgKeyAndChannel(orgKey, channel)) {
            byModule.computeIfAbsent(translation.getModuleKey(), key -> new TreeMap<>())
                    .put(translation.getLocale(), translation.getMessages());
        }
        return byModule;
    }

    private Map<String, ModuleDefinition> byKey(String orgKey, Channel channel) {
        return repository.findByOrgKeyAndChannel(orgKey, channel).stream()
                .collect(Collectors.toMap(ModuleDefinition::getKey, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    /** The authored content only: neither the channel, nor the version and timestamps every save moves. */
    static String fingerprint(ModuleDefinition module, Map<String, Map<String, String>> translations,
                              Map<String, String> assetHashes) {
        if (module == null) {
            return null;
        }
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("name", module.getName());
        content.put("translocoId", module.getTranslocoId());
        content.put("description", module.getDescription());
        content.put("translocoScope", module.getTranslocoScope());
        content.put("routes", module.getRoutes());
        // Only when there are any, so a module that never had translations keeps the fingerprint it had before they existed.
        if (translations != null && !translations.isEmpty()) {
            content.put("translations", translations);
        }
        if (assetHashes != null && !assetHashes.isEmpty()) {
            content.put("assets", assetHashes);
        }
        return CanonicalJson.of(content, Set.of());
    }
}
