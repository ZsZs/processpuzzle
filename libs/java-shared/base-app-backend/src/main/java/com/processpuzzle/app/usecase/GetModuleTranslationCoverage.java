package com.processpuzzle.app.usecase;

import com.processpuzzle.app.domain.ModuleDefinition;
import com.processpuzzle.app.domain.ModuleDefinitionRepository;
import com.processpuzzle.app.domain.ModuleTranslation;
import com.processpuzzle.app.domain.ModuleTranslationRepository;
import com.processpuzzle.app.usecase.exception.ModuleDefinitionNotFoundException;
import com.processpuzzle.app.usecase.service.ModuleTranslationKeys;
import com.processpuzzle.app.usecase.service.ModuleTranslationLanguages;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** How much of the module draft each supported language covers. */
@Service
@Transactional(readOnly = true)
public class GetModuleTranslationCoverage {

    private final ModuleDefinitionRepository modules;
    private final ModuleTranslationRepository translations;
    private final ModuleTranslationLanguages languages;
    private final OrganizationGuard guard;

    public GetModuleTranslationCoverage(ModuleDefinitionRepository modules, ModuleTranslationRepository translations,
                                        ModuleTranslationLanguages languages, OrganizationGuard guard) {
        this.modules = modules;
        this.translations = translations;
        this.languages = languages;
        this.guard = guard;
    }

    public List<ModuleTranslationCoverageResult> execute(String orgKey, String moduleKey) {
        guard.requireDesign(orgKey);
        ModuleDefinition module = modules.findByOrgKeyAndKeyAndChannel(orgKey, moduleKey, Channel.DRAFT)
                .orElseThrow(() -> new ModuleDefinitionNotFoundException(orgKey, moduleKey));
        Map<String, String> authored = ModuleTranslationKeys.of(module);
        Map<String, Map<String, String>> stored = translations
                .findByOrgKeyAndModuleKeyAndChannel(orgKey, moduleKey, Channel.DRAFT).stream()
                .collect(Collectors.toMap(ModuleTranslation::getLocale, ModuleTranslation::getMessages));
        String defaultLocale = languages.defaultLocale(orgKey);

        return languages.prepared(orgKey).stream().map(locale -> {
            if (locale.equalsIgnoreCase(defaultLocale)) {
                return new ModuleTranslationCoverageResult(locale, true, authored.size(), authored.size(), List.of());
            }
            Map<String, String> own = stored.getOrDefault(locale, Map.of());
            List<String> missing = authored.keySet().stream()
                    .filter(key -> own.get(key) == null || own.get(key).isBlank()).toList();
            long translated = authored.entrySet().stream()
                    .filter(entry -> own.get(entry.getKey()) != null && !own.get(entry.getKey()).isBlank()
                            && !own.get(entry.getKey()).equals(entry.getValue()))
                    .count();
            return new ModuleTranslationCoverageResult(locale, false, authored.size(), (int) translated, missing);
        }).toList();
    }
}
