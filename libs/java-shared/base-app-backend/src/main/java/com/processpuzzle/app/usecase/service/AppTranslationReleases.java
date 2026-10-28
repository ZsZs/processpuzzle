package com.processpuzzle.app.usecase.service;

import com.processpuzzle.app.domain.AppDefinitionTranslation;
import com.processpuzzle.app.domain.AppDefinitionTranslationRepository;
import com.processpuzzle.core.definition.Channel;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Publishes the translations of an app together with the app, as {@link AppAssetReleases} does for its assets: the
 * released bundles are made equal to the draft ones — copied, replaced, and removed where the draft has dropped a
 * language — so the running application never shows a text its designer has not published.
 */
@Component
public class AppTranslationReleases {

    private final AppDefinitionTranslationRepository translations;

    public AppTranslationReleases(AppDefinitionTranslationRepository translations) {
        this.translations = translations;
    }

    /** Makes the released bundles of the app equal to its draft ones. */
    public void release(String orgKey, String appId) {
        Map<String, AppDefinitionTranslation> released = translations
                .findByOrgKeyAndAppIdAndChannel(orgKey, appId, Channel.RELEASED).stream()
                .collect(Collectors.toMap(AppDefinitionTranslation::getLocale, Function.identity()));
        Set<String> kept = new TreeSet<>();
        for (AppDefinitionTranslation draft : translations.findByOrgKeyAndAppIdAndChannel(orgKey, appId, Channel.DRAFT)) {
            kept.add(draft.getLocale());
            AppDefinitionTranslation current = released.get(draft.getLocale());
            if (current == null) {
                translations.save(new AppDefinitionTranslation(orgKey, appId, Channel.RELEASED, draft.getLocale(), draft.getMessages()));
            } else {
                current.setMessages(draft.getMessages());
            }
        }
        released.forEach((locale, current) -> {
            if (!kept.contains(locale)) {
                translations.delete(current);
            }
        });
    }

    /** Deletes the bundles of the app in both channels, for deleting the app. */
    public void removeAll(String orgKey, String appId) {
        translations.deleteAll(translations.findByOrgKeyAndAppId(orgKey, appId));
    }

    /**
     * App id to language to the bundle's canonical text, the languages and keys sorted so that equal content
     * fingerprints equally.
     */
    public Map<String, Map<String, String>> byApp(String orgKey, Channel channel) {
        Map<String, Map<String, String>> byApp = new LinkedHashMap<>();
        for (AppDefinitionTranslation translation : translations.findByOrgKeyAndChannel(orgKey, channel)) {
            byApp.computeIfAbsent(translation.getAppId(), id -> new TreeMap<>())
                    .put(translation.getLocale(), new TreeMap<>(translation.getMessages()).toString());
        }
        return byApp;
    }
}
