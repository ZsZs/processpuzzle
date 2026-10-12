package com.processpuzzle.app.adapter.inbound;

import com.processpuzzle.app.domain.ModuleDefinition;
import com.processpuzzle.app.domain.ModuleDefinitionRepository;
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
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Releases modules: copies each draft onto its released row, and deletes the released rows whose
 * draft was deleted. A module's drafts were validated when saved, so there is nothing more to check.
 */
@Component
public class ModuleReleaseParticipant implements ReleaseParticipant {

    private final ModuleDefinitionRepository repository;

    public ModuleReleaseParticipant(ModuleDefinitionRepository repository) {
        this.repository = repository;
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

        List<PendingChange> pending = new ArrayList<>();
        for (String key : keys) {
            PendingChange change = PendingChange.between(kind(), key,
                    fingerprint(drafts.get(key)), fingerprint(released.get(key)));
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
        });
        released.forEach((key, current) -> {
            if (!drafts.containsKey(key)) {
                repository.delete(current);
            }
        });
    }

    private Map<String, ModuleDefinition> byKey(String orgKey, Channel channel) {
        return repository.findByOrgKeyAndChannel(orgKey, channel).stream()
                .collect(Collectors.toMap(ModuleDefinition::getKey, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    /** The authored content only: neither the channel, nor the version and timestamps every save moves. */
    static String fingerprint(ModuleDefinition module) {
        if (module == null) {
            return null;
        }
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("name", module.getName());
        content.put("translocoId", module.getTranslocoId());
        content.put("description", module.getDescription());
        content.put("translocoScope", module.getTranslocoScope());
        content.put("routes", module.getRoutes());
        return CanonicalJson.of(content, Set.of());
    }
}
