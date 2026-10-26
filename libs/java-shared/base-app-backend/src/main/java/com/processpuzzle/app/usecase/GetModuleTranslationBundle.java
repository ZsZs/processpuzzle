package com.processpuzzle.app.usecase;

import com.processpuzzle.app.domain.ModuleDefinitionRepository;
import com.processpuzzle.app.domain.ModuleTranslation;
import com.processpuzzle.app.domain.ModuleTranslationRepository;
import com.processpuzzle.app.usecase.exception.ModuleDefinitionNotFoundException;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a module's transloco scope loads at run time: the stored translations of one language and channel, as
 * they were uploaded. An empty bundle is an answer, not an error — the frontend falls back to the authored
 * text — but a module that does not exist in that channel is 404, as everywhere else.
 */
@Service
@Transactional(readOnly = true)
public class GetModuleTranslationBundle {

    private final ModuleDefinitionRepository modules;
    private final ModuleTranslationRepository translations;
    private final OrganizationGuard guard;

    public GetModuleTranslationBundle(ModuleDefinitionRepository modules, ModuleTranslationRepository translations,
                                      OrganizationGuard guard) {
        this.modules = modules;
        this.translations = translations;
        this.guard = guard;
    }

    public Map<String, String> execute(String orgKey, String moduleKey, String locale, Channel channel) {
        FindAllModuleDefinitions.requireReader(guard, orgKey, channel);
        if (!modules.existsByOrgKeyAndKeyAndChannel(orgKey, moduleKey, channel)) {
            throw new ModuleDefinitionNotFoundException(orgKey, moduleKey);
        }
        return translations.findByOrgKeyAndModuleKeyAndChannelAndLocale(orgKey, moduleKey, channel, locale)
                .map(ModuleTranslation::getMessages)
                .orElseGet(Map::of);
    }
}
