package com.processpuzzle.app.adapter.inbound;

import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.app.domain.AppDefinitionTranslation;
import com.processpuzzle.app.domain.AppDefinitionTranslationRepository;
import com.processpuzzle.app.domain.ModuleDefinitionRepository;
import com.processpuzzle.app.domain.ModuleTranslation;
import com.processpuzzle.app.domain.ModuleTranslationRepository;
import com.processpuzzle.app.usecase.ModuleTranslationUploadResult;
import com.processpuzzle.app.usecase.UploadAppTranslations;
import com.processpuzzle.app.usecase.UploadModuleTranslations;
import com.processpuzzle.app.usecase.service.AppTranslationReleases;
import com.processpuzzle.core.definition.CanonicalJson;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.definition.DefinitionImportParticipant;
import com.processpuzzle.core.definition.DefinitionRemoval;
import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.ImportedItem;
import com.processpuzzle.core.definition.ParticipantResult;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Imports the translated texts of a Business Starter's modules and apps —
 * {@code translations/modules/<key>/<locale>.json} and {@code translations/apps/<id>/<locale>.json}, see
 * {@link BundleOwnedPath} — through {@link UploadModuleTranslations} and {@link UploadAppTranslations}, so a file meets
 * the rules of one uploaded from the module or app form: a flat or nested JSON object and a well-formed language. A
 * module's file may hold only the texts the module authored (its name, description and route titles); a key it does
 * not author is refused rather than dropped, so a starter author hears about a text that would never be shown. An
 * app's file holds whatever its widgets name. The key is {@code modules/<key>/<locale>} or
 * {@code apps/<id>/<locale>}.
 *
 * <p>Runs after the modules and apps, which are released when they are imported, so each file releases its owner
 * again, right after it is stored: the released translations are what the running application loads. A file whose
 * module or app the bundle does not define is refused.
 */
@Component
public class TranslationImportParticipant implements DefinitionImportParticipant {

    private static final String JSON_EXTENSION = ".json";

    private final UploadModuleTranslations upload;
    private final UploadAppTranslations uploadApp;
    private final ModuleTranslationRepository translations;
    private final AppDefinitionTranslationRepository appTranslations;
    private final ModuleDefinitionRepository modules;
    private final AppDefinitionRepository apps;
    private final ModuleReleaseParticipant moduleRelease;
    private final AppTranslationReleases appRelease;

    public TranslationImportParticipant(UploadModuleTranslations upload, UploadAppTranslations uploadApp,
                                        ModuleTranslationRepository translations,
                                        AppDefinitionTranslationRepository appTranslations,
                                        ModuleDefinitionRepository modules, AppDefinitionRepository apps,
                                        ModuleReleaseParticipant moduleRelease, AppTranslationReleases appRelease) {
        this.upload = upload;
        this.uploadApp = uploadApp;
        this.translations = translations;
        this.appTranslations = appTranslations;
        this.modules = modules;
        this.apps = apps;
        this.moduleRelease = moduleRelease;
        this.appRelease = appRelease;
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
        return write(orgKey, fileName, json, true);
    }

    @Override
    public boolean supportsUpgrade() {
        return true;
    }

    /** A translation file is one definition: the key of its module or app and its locale. */
    @Override
    public Set<String> keysIn(String fileName, byte[] json) {
        return keyOf(fileName).map(Set::of).orElse(Set.of());
    }

    /**
     * Writes the file's draft if its key is wanted, and does not release the owner: an install makes the texts live
     * at once, an upgrade leaves them as drafts for the organization's release.
     */
    @Override
    public ParticipantResult applyOnly(String orgKey, String fileName, byte[] json, Set<String> keys) {
        return keyOf(fileName).filter(keys::contains).isPresent()
                ? write(orgKey, fileName, json, false) : new ParticipantResult(List.of(), List.of());
    }

    /** Deletes the draft bundles; the released ones go with the next release. */
    @Override
    public DefinitionRemoval removeDrafts(String orgKey, Set<String> keys) {
        return DefinitionRemoval.removing(keys, key -> removeDraft(orgKey, key));
    }

    private void removeDraft(String orgKey, String key) {
        String[] parts = key.split("/", 3);
        if (parts.length != 3) {
            throw new IllegalStateException("not a translation key: '" + key + "'");
        }
        if (BundleOwnedPath.APPS.equals(parts[0])) {
            appTranslations.delete(appTranslations.findByOrgKeyAndAppIdAndChannelAndLocale(orgKey, parts[1], Channel.DRAFT, parts[2])
                    .orElseThrow(() -> new IllegalStateException("no draft of '" + key + "'")));
        } else {
            translations.delete(translations.findByOrgKeyAndModuleKeyAndChannelAndLocale(orgKey, parts[1], Channel.DRAFT, parts[2])
                    .orElseThrow(() -> new IllegalStateException("no draft of '" + key + "'")));
        }
    }

    private static Optional<String> keyOf(String fileName) {
        return BundleOwnedPath.parse(fileName)
                .filter(path -> path.rest().endsWith(JSON_EXTENSION) && !path.rest().contains("/"))
                .map(path -> key(path.isApp() ? BundleOwnedPath.APPS : BundleOwnedPath.MODULES, path.ownerKey(),
                        path.rest().substring(0, path.rest().length() - JSON_EXTENSION.length())));
    }

    private ParticipantResult write(String orgKey, String fileName, byte[] json, boolean release) {
        BundleOwnedPath path = BundleOwnedPath.parse(fileName).orElse(null);
        if (path == null || !path.rest().endsWith(JSON_EXTENSION) || path.rest().contains("/")) {
            return ParticipantResult.rejected(List.of(fileName + ": a translation is <group>/modules/<module key>/<locale>.json "
                    + "or <group>/apps/<app id>/<locale>.json."));
        }
        String locale = path.rest().substring(0, path.rest().length() - JSON_EXTENSION.length());
        return path.isApp() ? applyToApp(orgKey, fileName, path, locale, json, release)
                : applyToModule(orgKey, fileName, path, locale, json, release);
    }

    private ParticipantResult applyToApp(String orgKey, String fileName, BundleOwnedPath path, String locale, byte[] json, boolean release) {
        if (!apps.existsByOrgKeyAndId(orgKey, path.ownerKey())) {
            return ParticipantResult.rejected(List.of(fileName + ": the bundle defines no app '" + path.ownerKey() + "'."));
        }
        boolean existed = appTranslations.findByOrgKeyAndAppIdAndChannelAndLocale(orgKey, path.ownerKey(), Channel.DRAFT, locale).isPresent();
        try {
            uploadApp.execute(orgKey, path.ownerKey(), path.rest(), new ByteArrayInputStream(json));
        } catch (RuntimeException e) {
            return ParticipantResult.rejected(List.of(fileName + ": " + e.getMessage()));
        }
        if (release) {
            appRelease.release(orgKey, path.ownerKey());
        }
        return new ParticipantResult(List.of(new ImportedItem(key(BundleOwnedPath.APPS, path.ownerKey(), locale),
                existed ? ImportedItem.Action.UPDATE : ImportedItem.Action.CREATE)), List.of());
    }

    private ParticipantResult applyToModule(String orgKey, String fileName, BundleOwnedPath path, String locale, byte[] json, boolean release) {
        if (!modules.existsByOrgKeyAndKeyAndChannel(orgKey, path.ownerKey(), Channel.DRAFT)) {
            return ParticipantResult.rejected(List.of(fileName + ": the bundle defines no module '" + path.ownerKey() + "'."));
        }
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
        if (release) {
            moduleRelease.release(orgKey);
        }
        return new ParticipantResult(List.of(new ImportedItem(key(BundleOwnedPath.MODULES, path.ownerKey(), locale),
                existed ? ImportedItem.Action.UPDATE : ImportedItem.Action.CREATE)), List.of());
    }

    /** Deletes the translations of every module and app, in both channels. Flushed for the reason {@code AssetImportParticipant} gives. */
    @Override
    public List<String> removeAll(String orgKey) {
        List<String> keys = new ArrayList<>(drafts(orgKey).keySet());
        for (Channel channel : Channel.values()) {
            translations.deleteAll(translations.findByOrgKeyAndChannel(orgKey, channel));
            appTranslations.deleteAll(appTranslations.findByOrgKeyAndChannel(orgKey, channel));
        }
        translations.flush();
        return keys;
    }

    @Override
    public Map<String, String> fingerprints(String orgKey, Set<String> keys) {
        Map<String, String> fingerprints = new LinkedHashMap<>();
        drafts(orgKey).forEach((key, messages) -> {
            if (keys.contains(key)) {
                fingerprints.put(key, CanonicalJson.of(new LinkedHashMap<String, Object>(Map.of("messages", messages)), Set.of()));
            }
        });
        return fingerprints;
    }

    /** The draft bundles of every module and app, by the key the importer records them under. */
    private Map<String, Map<String, String>> drafts(String orgKey) {
        Map<String, Map<String, String>> byKey = new LinkedHashMap<>();
        for (ModuleTranslation translation : translations.findByOrgKeyAndChannel(orgKey, Channel.DRAFT)) {
            byKey.put(key(BundleOwnedPath.MODULES, translation.getModuleKey(), translation.getLocale()), translation.getMessages());
        }
        for (AppDefinitionTranslation translation : appTranslations.findByOrgKeyAndChannel(orgKey, Channel.DRAFT)) {
            byKey.put(key(BundleOwnedPath.APPS, translation.getAppId(), translation.getLocale()), translation.getMessages());
        }
        return byKey;
    }

    private static String key(String ownerKind, String ownerKey, String locale) {
        return ownerKind + "/" + ownerKey + "/" + locale;
    }
}
