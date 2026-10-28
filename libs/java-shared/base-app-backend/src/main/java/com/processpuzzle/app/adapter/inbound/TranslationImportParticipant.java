package com.processpuzzle.app.adapter.inbound;

import com.processpuzzle.app.domain.ModuleDefinitionRepository;
import com.processpuzzle.app.domain.ModuleTranslation;
import com.processpuzzle.app.domain.ModuleTranslationRepository;
import com.processpuzzle.app.usecase.ModuleTranslationUploadResult;
import com.processpuzzle.app.usecase.UploadModuleTranslations;
import com.processpuzzle.core.definition.CanonicalJson;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.definition.DefinitionImportParticipant;
import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.ImportedItem;
import com.processpuzzle.core.definition.ParticipantResult;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Imports the translated texts of a Business Starter's modules — {@code translations/modules/<key>/<locale>.json},
 * see {@link BundleOwnedPath} — through {@link UploadModuleTranslations}, so a file meets the rules of one uploaded
 * from the module form: a flat or nested JSON object, a well-formed language, and only the texts the module authored
 * (its name, description and route titles). A key the module does not author is refused rather than dropped, so a
 * starter author hears about a text that would never be shown. The key is {@code modules/<key>/<locale>}.
 *
 * <p>Runs right after the modules, which are released when they are imported, so each file releases the modules
 * again, right after it is stored: the released translations are what the running application loads.
 *
 * <p>There is no counterpart for an application: the texts of an application's own pages come from the bundles the
 * platform ships, and no tenant store holds them. A file under {@code translations/apps/} is refused.
 */
@Component
public class TranslationImportParticipant implements DefinitionImportParticipant {

    private final UploadModuleTranslations upload;
    private final ModuleTranslationRepository translations;
    private final ModuleDefinitionRepository modules;
    private final ModuleReleaseParticipant moduleRelease;

    public TranslationImportParticipant(UploadModuleTranslations upload, ModuleTranslationRepository translations,
                                        ModuleDefinitionRepository modules, ModuleReleaseParticipant moduleRelease) {
        this.upload = upload;
        this.translations = translations;
        this.modules = modules;
        this.moduleRelease = moduleRelease;
    }

    @Override
    public String kind() {
        return DefinitionKinds.TRANSLATION;
    }

    @Override
    public int order() {
        return DefinitionKinds.TRANSLATION_ORDER;
    }

    @Override
    public ParticipantResult apply(String orgKey, String fileName, byte[] json) {
        BundleOwnedPath path = BundleOwnedPath.parse(fileName).orElse(null);
        if (path == null || !path.rest().endsWith(".json") || path.rest().contains("/")) {
            return ParticipantResult.rejected(List.of(fileName + ": a translation is <group>/modules/<module key>/<locale>.json."));
        }
        if (path.isApp()) {
            return ParticipantResult.rejected(List.of(fileName + ": only a module's texts can be imported; an application's own "
                    + "pages are translated by the platform's bundles."));
        }
        if (!modules.existsByOrgKeyAndKeyAndChannel(orgKey, path.ownerKey(), Channel.DRAFT)) {
            return ParticipantResult.rejected(List.of(fileName + ": the bundle defines no module '" + path.ownerKey() + "'."));
        }
        String locale = path.rest().substring(0, path.rest().length() - ".json".length());
        boolean existed = translations.findByOrgKeyAndModuleKeyAndChannelAndLocale(orgKey, path.ownerKey(), Channel.DRAFT, locale).isPresent();
        ModuleTranslationUploadResult result;
        try {
            result = upload.execute(orgKey, path.ownerKey(), path.rest(), new ByteArrayInputStream(json));
        } catch (RuntimeException e) {
            return ParticipantResult.rejected(List.of(fileName + ": " + e.getMessage()));
        }
        List<String> errors = new ArrayList<>();
        result.languages().forEach(language -> language.unknownKeys().forEach(key -> errors.add(fileName + ": '" + key
                + "' is not a text module '" + path.ownerKey() + "' authors, so it would never be shown.")));
        if (!errors.isEmpty()) {
            return ParticipantResult.rejected(errors);
        }
        moduleRelease.release(orgKey);
        return new ParticipantResult(List.of(new ImportedItem(key(path.ownerKey(), locale),
                existed ? ImportedItem.Action.UPDATE : ImportedItem.Action.CREATE)), List.of());
    }

    /** Deletes the translations of every module, in both channels. Flushed for the reason {@code AssetImportParticipant} gives. */
    @Override
    public List<String> removeAll(String orgKey) {
        List<String> keys = new ArrayList<>(drafts(orgKey).keySet());
        for (Channel channel : Channel.values()) {
            translations.deleteAll(translations.findByOrgKeyAndChannel(orgKey, channel));
        }
        translations.flush();
        return keys;
    }

    @Override
    public Map<String, String> fingerprints(String orgKey, Set<String> keys) {
        Map<String, String> fingerprints = new LinkedHashMap<>();
        drafts(orgKey).forEach((key, translation) -> {
            if (keys.contains(key)) {
                fingerprints.put(key, CanonicalJson.of(new LinkedHashMap<String, Object>(Map.of("messages", translation.getMessages())), Set.of()));
            }
        });
        return fingerprints;
    }

    private Map<String, ModuleTranslation> drafts(String orgKey) {
        Map<String, ModuleTranslation> byKey = new LinkedHashMap<>();
        translations.findByOrgKeyAndChannel(orgKey, Channel.DRAFT)
                .forEach(translation -> byKey.put(key(translation.getModuleKey(), translation.getLocale()), translation));
        return byKey;
    }

    private static String key(String moduleKey, String locale) {
        return BundleOwnedPath.MODULES + "/" + moduleKey + "/" + locale;
    }
}
