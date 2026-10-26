package com.processpuzzle.app.usecase;

import com.processpuzzle.app.domain.ModuleDefinition;
import com.processpuzzle.app.domain.ModuleDefinitionRepository;
import com.processpuzzle.app.domain.ModuleTranslation;
import com.processpuzzle.app.domain.ModuleTranslationRepository;
import com.processpuzzle.app.usecase.exception.ModuleDefinitionNotFoundException;
import com.processpuzzle.app.usecase.service.ModuleTranslationFiles;
import com.processpuzzle.app.usecase.service.ModuleTranslationKeys;
import com.processpuzzle.app.usecase.service.ModuleTranslationLanguages;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prepares the files a translator works on: one per supported language, holding every translatable item of
 * the module draft. Each value is the uploaded translation when there is one, else the authored text — so
 * the default language is complete and every other one shows what is left to translate.
 *
 * <p>The keys come from the module, not from what was stored, so a key the module dropped is not written
 * and a stored value for it is simply left behind.
 */
@Service
@Transactional(readOnly = true)
public class DownloadModuleTranslations {

    private final ModuleDefinitionRepository modules;
    private final ModuleTranslationRepository translations;
    private final ModuleTranslationLanguages languages;
    private final OrganizationGuard guard;

    public DownloadModuleTranslations(ModuleDefinitionRepository modules, ModuleTranslationRepository translations,
                                      ModuleTranslationLanguages languages, OrganizationGuard guard) {
        this.modules = modules;
        this.translations = translations;
        this.languages = languages;
        this.guard = guard;
    }

    /** The zip archive. */
    public byte[] execute(String orgKey, String moduleKey) {
        guard.requireDesign(orgKey);
        ModuleDefinition module = modules.findByOrgKeyAndKeyAndChannel(orgKey, moduleKey, Channel.DRAFT)
                .orElseThrow(() -> new ModuleDefinitionNotFoundException(orgKey, moduleKey));
        Map<String, String> authored = ModuleTranslationKeys.of(module);
        Map<String, Map<String, String>> stored = translations
                .findByOrgKeyAndModuleKeyAndChannel(orgKey, moduleKey, Channel.DRAFT).stream()
                .collect(Collectors.toMap(ModuleTranslation::getLocale, ModuleTranslation::getMessages));

        Map<String, Map<String, String>> files = new LinkedHashMap<>();
        for (String locale : languages.prepared(orgKey)) {
            Map<String, String> own = stored.getOrDefault(locale, Map.of());
            files.put(locale, authored.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                    entry -> translated(own.get(entry.getKey()), entry.getValue()), (a, b) -> a, LinkedHashMap::new)));
        }
        return ModuleTranslationFiles.zip(files);
    }

    private static String translated(String stored, String authored) {
        return stored == null || stored.isBlank() ? authored : stored;
    }
}
