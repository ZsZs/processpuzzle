package com.processpuzzle.app.usecase;

import com.processpuzzle.app.domain.ModuleDefinition;
import com.processpuzzle.app.domain.ModuleDefinitionRepository;
import com.processpuzzle.app.domain.ModuleTranslation;
import com.processpuzzle.app.domain.ModuleTranslationRepository;
import com.processpuzzle.app.usecase.exception.ModuleDefinitionNotFoundException;
import com.processpuzzle.app.usecase.exception.ModuleTranslationRejectedException;
import com.processpuzzle.app.usecase.service.ModuleTranslationFiles;
import com.processpuzzle.app.usecase.service.ModuleTranslationKeys;
import com.processpuzzle.app.usecase.service.ModuleTranslationLanguages;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores the translated files a user uploaded, onto the module's draft. The release copies them on.
 *
 * <p>All-or-nothing: every file is checked before anything is written, so a bad language in the archive
 * does not leave the others half applied.
 */
@Service
@Transactional
public class UploadModuleTranslations {

    private final ModuleDefinitionRepository modules;
    private final ModuleTranslationRepository translations;
    private final ModuleTranslationLanguages languages;
    private final OrganizationGuard guard;

    public UploadModuleTranslations(ModuleDefinitionRepository modules, ModuleTranslationRepository translations,
                                    ModuleTranslationLanguages languages, OrganizationGuard guard) {
        this.modules = modules;
        this.translations = translations;
        this.languages = languages;
        this.guard = guard;
    }

    public ModuleTranslationUploadResult execute(String orgKey, String moduleKey, String filename, InputStream upload) {
        guard.requireDesign(orgKey);
        ModuleDefinition module = modules.findByOrgKeyAndKeyAndChannel(orgKey, moduleKey, Channel.DRAFT)
                .orElseThrow(() -> new ModuleDefinitionNotFoundException(orgKey, moduleKey));
        Map<String, String> authored = ModuleTranslationKeys.of(module);
        String defaultLocale = languages.defaultLocale(orgKey);

        Map<String, Map<String, String>> files = ModuleTranslationFiles.read(filename, upload);
        Map<String, Map<String, String>> accepted = new LinkedHashMap<>();
        files.forEach((locale, messages) -> {
            String spelling = languages.accept(orgKey, locale).orElseThrow(() ->
                    new ModuleTranslationRejectedException("The language '" + locale + "' is not supported."));
            if (accepted.put(spelling, messages) != null) {
                throw new ModuleTranslationRejectedException("The upload holds the language '" + spelling + "' twice.");
            }
        });

        List<ModuleTranslationUploadResult.Language> reports = new ArrayList<>();
        accepted.forEach((locale, messages) -> reports.add(store(orgKey, moduleKey, locale, messages, authored,
                locale.equalsIgnoreCase(defaultLocale))));
        return new ModuleTranslationUploadResult(reports);
    }

    private ModuleTranslationUploadResult.Language store(String orgKey, String moduleKey, String locale,
                                                         Map<String, String> messages, Map<String, String> authored,
                                                         boolean isDefault) {
        Map<String, String> stored = new LinkedHashMap<>();
        List<String> unknown = new ArrayList<>();
        messages.forEach((key, value) -> {
            if (authored.containsKey(key)) {
                stored.put(key, value);
            } else {
                unknown.add(key);
            }
        });
        List<String> missing = authored.keySet().stream().filter(key -> !stored.containsKey(key)).toList();
        List<String> untranslated = isDefault
                ? List.of()
                : stored.entrySet().stream().filter(entry -> entry.getValue().equals(authored.get(entry.getKey())))
                        .map(Map.Entry::getKey).toList();

        translations.findByOrgKeyAndModuleKeyAndChannelAndLocale(orgKey, moduleKey, Channel.DRAFT, locale)
                .ifPresentOrElse(existing -> existing.setMessages(stored),
                        () -> translations.save(new ModuleTranslation(orgKey, moduleKey, Channel.DRAFT, locale, stored)));
        return new ModuleTranslationUploadResult.Language(locale, stored.size(), unknown, missing, untranslated);
    }
}
