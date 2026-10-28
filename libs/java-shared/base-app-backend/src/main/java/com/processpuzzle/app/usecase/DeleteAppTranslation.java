package com.processpuzzle.app.usecase;

import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.app.domain.AppDefinitionTranslationRepository;
import com.processpuzzle.app.usecase.exception.AppDefinitionNotFoundException;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Removes the draft bundle of one language; publishing the app releases the removal. Idempotent. */
@Service
@Transactional
public class DeleteAppTranslation {

    private final AppDefinitionRepository apps;
    private final AppDefinitionTranslationRepository translations;
    private final OrganizationGuard guard;

    public DeleteAppTranslation(AppDefinitionRepository apps, AppDefinitionTranslationRepository translations,
                                OrganizationGuard guard) {
        this.apps = apps;
        this.translations = translations;
        this.guard = guard;
    }

    public void execute(String orgKey, String appId, String locale) {
        guard.requireDesign(orgKey);
        if (!apps.existsByOrgKeyAndId(orgKey, appId)) {
            throw new AppDefinitionNotFoundException(orgKey, appId);
        }
        translations.findByOrgKeyAndAppIdAndChannelAndLocale(orgKey, appId, Channel.DRAFT, locale)
                .ifPresent(translations::delete);
    }
}
