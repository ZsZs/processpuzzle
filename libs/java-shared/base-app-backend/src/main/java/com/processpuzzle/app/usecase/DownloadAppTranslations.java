package com.processpuzzle.app.usecase;

import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.app.domain.AppDefinitionTranslation;
import com.processpuzzle.app.domain.AppDefinitionTranslationRepository;
import com.processpuzzle.app.usecase.exception.AppDefinitionNotFoundException;
import com.processpuzzle.app.usecase.service.ModuleTranslationFiles;
import com.processpuzzle.app.usecase.service.ModuleTranslationLanguages;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The files a translator works on: one per supported language, holding what the app's draft has stored for it —
 * and an empty one for a language nothing is stored for, to be filled in. There are no authored texts to prefill
 * from, as a module has: an app's texts exist only as the files its designer uploads.
 *
 * <p>A language stored but no longer supported is still written, so a download never hides a bundle.
 */
@Service
@Transactional(readOnly = true)
public class DownloadAppTranslations {

    private final AppDefinitionRepository apps;
    private final AppDefinitionTranslationRepository translations;
    private final ModuleTranslationLanguages languages;
    private final OrganizationGuard guard;

    public DownloadAppTranslations(AppDefinitionRepository apps, AppDefinitionTranslationRepository translations,
                                   ModuleTranslationLanguages languages, OrganizationGuard guard) {
        this.apps = apps;
        this.translations = translations;
        this.languages = languages;
        this.guard = guard;
    }

    /** The zip archive. */
    public byte[] execute(String orgKey, String appId) {
        guard.requireDesign(orgKey);
        if (!apps.existsByOrgKeyAndId(orgKey, appId)) {
            throw new AppDefinitionNotFoundException(orgKey, appId);
        }
        Map<String, Map<String, String>> stored = translations
                .findByOrgKeyAndAppIdAndChannel(orgKey, appId, Channel.DRAFT).stream()
                .collect(Collectors.toMap(AppDefinitionTranslation::getLocale, AppDefinitionTranslation::getMessages));
        Map<String, Map<String, String>> files = new LinkedHashMap<>();
        for (String locale : languages.prepared(orgKey)) {
            files.put(locale, stored.getOrDefault(locale, Map.of()));
        }
        stored.forEach(files::putIfAbsent);
        return ModuleTranslationFiles.zip(files);
    }
}
