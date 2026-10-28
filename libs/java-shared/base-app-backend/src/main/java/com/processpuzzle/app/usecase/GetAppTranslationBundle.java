package com.processpuzzle.app.usecase;

import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.app.domain.AppDefinitionTranslation;
import com.processpuzzle.app.domain.AppDefinitionTranslationRepository;
import com.processpuzzle.app.usecase.exception.AppDefinitionNotFoundException;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the running application merges into its translations: the stored texts of one app, language and channel,
 * as they were uploaded. An empty bundle is an answer, not an error — the widgets then show their keys — but an
 * app that does not exist is 404, as everywhere else.
 */
@Service
@Transactional(readOnly = true)
public class GetAppTranslationBundle {

    private final AppDefinitionRepository apps;
    private final AppDefinitionTranslationRepository translations;
    private final OrganizationGuard guard;

    public GetAppTranslationBundle(AppDefinitionRepository apps, AppDefinitionTranslationRepository translations,
                                   OrganizationGuard guard) {
        this.apps = apps;
        this.translations = translations;
        this.guard = guard;
    }

    public Map<String, String> execute(String orgKey, String appId, String locale, Channel channel) {
        FindAllModuleDefinitions.requireReader(guard, orgKey, channel);
        if (!apps.existsByOrgKeyAndId(orgKey, appId)) {
            throw new AppDefinitionNotFoundException(orgKey, appId);
        }
        return translations.findByOrgKeyAndAppIdAndChannelAndLocale(orgKey, appId, channel, locale)
                .map(AppDefinitionTranslation::getMessages)
                .orElseGet(Map::of);
    }
}
