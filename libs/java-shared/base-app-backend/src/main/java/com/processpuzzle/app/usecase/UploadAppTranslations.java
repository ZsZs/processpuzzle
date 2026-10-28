package com.processpuzzle.app.usecase;

import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.app.domain.AppDefinitionTranslation;
import com.processpuzzle.app.domain.AppDefinitionTranslationRepository;
import com.processpuzzle.app.usecase.exception.AppDefinitionNotFoundException;
import com.processpuzzle.app.usecase.exception.ModuleTranslationRejectedException;
import com.processpuzzle.app.usecase.service.AppTranslationKeys;
import com.processpuzzle.app.usecase.service.ModuleTranslationFiles;
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
 * Stores the translated files a user uploaded, onto the app's draft: {@link UploadModuleTranslations} one level up,
 * reading the same zip or lone {@code <locale>.json}. Publishing the app copies them on.
 *
 * <p>All-or-nothing: every file is checked before anything is written. Each language's bundle is replaced as a
 * whole. Unlike a module's, the keys are not checked against the definition — see {@link AppTranslationKeys}.
 */
@Service
@Transactional
public class UploadAppTranslations {

    private final AppDefinitionRepository apps;
    private final AppDefinitionTranslationRepository translations;
    private final ModuleTranslationLanguages languages;
    private final OrganizationGuard guard;

    public UploadAppTranslations(AppDefinitionRepository apps, AppDefinitionTranslationRepository translations,
                                 ModuleTranslationLanguages languages, OrganizationGuard guard) {
        this.apps = apps;
        this.translations = translations;
        this.languages = languages;
        this.guard = guard;
    }

    public AppTranslationUploadResult execute(String orgKey, String appId, String filename, InputStream upload) {
        guard.requireDesign(orgKey);
        if (!apps.existsByOrgKeyAndId(orgKey, appId)) {
            throw new AppDefinitionNotFoundException(orgKey, appId);
        }
        Map<String, Map<String, String>> accepted = new LinkedHashMap<>();
        ModuleTranslationFiles.read(filename, upload).forEach((locale, messages) -> {
            String spelling = languages.accept(orgKey, locale).orElseThrow(() ->
                    new ModuleTranslationRejectedException("The language '" + locale + "' is not supported."));
            if (accepted.put(spelling, messages) != null) {
                throw new ModuleTranslationRejectedException("The upload holds the language '" + spelling + "' twice.");
            }
            AppTranslationKeys.require(spelling, messages);
        });

        List<AppTranslationUploadResult.Language> reports = new ArrayList<>();
        accepted.forEach((locale, messages) -> {
            translations.findByOrgKeyAndAppIdAndChannelAndLocale(orgKey, appId, Channel.DRAFT, locale)
                    .ifPresentOrElse(existing -> existing.setMessages(messages),
                            () -> translations.save(new AppDefinitionTranslation(orgKey, appId, Channel.DRAFT, locale, messages)));
            reports.add(new AppTranslationUploadResult.Language(locale, messages.size()));
        });
        return new AppTranslationUploadResult(reports);
    }
}
