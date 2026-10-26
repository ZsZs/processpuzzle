package com.processpuzzle.app.adapter.inbound;

import com.processpuzzle.app.domain.ModuleDefinition;
import com.processpuzzle.app.domain.ModuleDefinitionRepository;
import com.processpuzzle.app.domain.ModuleTranslation;
import com.processpuzzle.app.domain.ModuleTranslationRepository;
import com.processpuzzle.core.definition.CanonicalJson;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.DefinitionReference;
import com.processpuzzle.core.definition.PendingChange;
import com.processpuzzle.core.definition.ReleaseCandidate;
import com.processpuzzle.core.definition.ReleaseParticipant;
import com.processpuzzle.core.definition.ReleaseProblem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Releases modules: copies each draft onto its released row, and deletes the released rows whose
 * draft was deleted. A module's drafts were validated when saved, so there is nothing more to check.
 *
 * <p>A module's uploaded translations travel with it: they are part of what is pending, and release
 * copies them onto the released channel, language by language, so that what the running application loads
 * is always the translations that were released together with the routes they name.
 */
@Component
public class ModuleReleaseParticipant implements ReleaseParticipant {

    private final ModuleDefinitionRepository repository;
    private final ModuleTranslationRepository translations;

    public ModuleReleaseParticipant(ModuleDefinitionRepository repository, ModuleTranslationRepository translations) {
        this.repository = repository;
        this.translations = translations;
    }

    @Override
    public String kind() {
        return DefinitionKinds.MODULE;
    }

    @Override
    public int order() {
        return DefinitionKinds.MODULE_ORDER;
    }

    @Override
    public Set<String> drafts(String orgKey) {
        return byKey(orgKey, Channel.DRAFT).keySet();
    }

    @Override
    public List<PendingChange> pending(String orgKey) {
        Map<String, ModuleDefinition> drafts = byKey(orgKey, Channel.DRAFT);
        Map<String, ModuleDefinition> released = byKey(orgKey, Channel.RELEASED);
        Set<String> keys = new TreeSet<>(drafts.keySet());
        keys.addAll(released.keySet());
        Map<String, Map<String, Map<String, String>>> draftTranslations = translationsByModule(orgKey, Channel.DRAFT);
        Map<String, Map<String, Map<String, String>>> releasedTranslations = translationsByModule(orgKey, Channel.RELEASED);

        List<PendingChange> pending = new ArrayList<>();
        for (String key : keys) {
            PendingChange change = PendingChange.between(kind(), key,
                    fingerprint(drafts.get(key), draftTranslations.get(key)),
                    fingerprint(released.get(key), releasedTranslations.get(key)));
            if (change != null) {
                pending.add(change);
            }
        }
        return pending;
    }

    @Override
    public List<DefinitionReference> references(String orgKey) {
        return repository.findByOrgKeyAndChannel(orgKey, Channel.DRAFT).stream()
                .flatMap(module -> RouteReferences.of(kind(), module.getKey(), module.getRoutes()).stream())
                .toList();
    }

    @Override
    public List<ReleaseProblem> check(String orgKey, ReleaseCandidate candidate) {
        return List.of();
    }

    @Override
    public void release(String orgKey) {
        Map<String, ModuleDefinition> drafts = byKey(orgKey, Channel.DRAFT);
        Map<String, ModuleDefinition> released = byKey(orgKey, Channel.RELEASED);
        drafts.forEach((key, draft) -> {
            ModuleDefinition current = released.get(key);
            if (current == null) {
                repository.save(ModuleDefinition.releasedCopyOf(draft));
            } else {
                current.releaseFrom(draft);
            }
            releaseTranslations(orgKey, key);
        });
        released.forEach((key, current) -> {
            if (!drafts.containsKey(key)) {
                repository.delete(current);
                translations.deleteByOrgKeyAndModuleKeyAndChannel(orgKey, key, Channel.RELEASED);
            }
        });
    }

    /** Updates in place rather than deleting and re-inserting: Hibernate runs inserts before deletes, which would collide on the key. */
    private void releaseTranslations(String orgKey, String moduleKey) {
        Map<String, ModuleTranslation> released = translations
                .findByOrgKeyAndModuleKeyAndChannel(orgKey, moduleKey, Channel.RELEASED).stream()
                .collect(Collectors.toMap(ModuleTranslation::getLocale, Function.identity()));
        Set<String> kept = new TreeSet<>();
        for (ModuleTranslation draft : translations.findByOrgKeyAndModuleKeyAndChannel(orgKey, moduleKey, Channel.DRAFT)) {
            kept.add(draft.getLocale());
            ModuleTranslation current = released.get(draft.getLocale());
            if (current == null) {
                translations.save(new ModuleTranslation(orgKey, moduleKey, Channel.RELEASED, draft.getLocale(),
                        draft.getMessages()));
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

    /** Module key to language to messages, the languages sorted so that equal content fingerprints equally. */
    private Map<String, Map<String, Map<String, String>>> translationsByModule(String orgKey, Channel channel) {
        Map<String, Map<String, Map<String, String>>> byModule = new LinkedHashMap<>();
        for (ModuleTranslation translation : translations.findByOrgKeyAndChannel(orgKey, channel)) {
            byModule.computeIfAbsent(translation.getModuleKey(), key -> new TreeMap<>())
                    .put(translation.getLocale(), translation.getMessages());
        }
        return byModule;
    }

    private Map<String, ModuleDefinition> byKey(String orgKey, Channel channel) {
        return repository.findByOrgKeyAndChannel(orgKey, channel).stream()
                .collect(Collectors.toMap(ModuleDefinition::getKey, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    /** The authored content only: neither the channel, nor the version and timestamps every save moves. */
    static String fingerprint(ModuleDefinition module, Map<String, Map<String, String>> translations) {
        if (module == null) {
            return null;
        }
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("name", module.getName());
        content.put("translocoId", module.getTranslocoId());
        content.put("description", module.getDescription());
        content.put("translocoScope", module.getTranslocoScope());
        content.put("routes", module.getRoutes());
        // Only when there are any, so a module that never had translations keeps the fingerprint it had before they existed.
        if (translations != null && !translations.isEmpty()) {
            content.put("translations", translations);
        }
        return CanonicalJson.of(content, Set.of());
    }
}
