package com.processpuzzle.rule.adapter.inbound;

import com.processpuzzle.core.definition.CanonicalJson;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.DefinitionReference;
import com.processpuzzle.core.definition.PendingChange;
import com.processpuzzle.core.definition.ReleaseCandidate;
import com.processpuzzle.core.definition.ReleaseParticipant;
import com.processpuzzle.core.definition.ReleaseProblem;
import com.processpuzzle.rule.domain.RuleDefinition;
import com.processpuzzle.rule.domain.RuleDefinitionRepository;
import com.processpuzzle.rule.usecase.RuleReleaser;
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
 * Releases rules, keyed by id: copies each draft onto its released row, deletes the released rows whose draft was
 * deleted, and keeps the engine's registry in step. The running application evaluates the released rules only.
 *
 * <p>A rule judges data when it is saved and holds none itself, so no change to one strands existing objects: there is
 * nothing to refuse beyond what a save already validated. What a release does check is that a rule's parent — the
 * rule it {@code extends} — goes out with it.
 */
@Component
public class RuleReleaseParticipant implements ReleaseParticipant {

    private final RuleDefinitionRepository repository;
    private final RuleReleaser releaser;

    public RuleReleaseParticipant(RuleDefinitionRepository repository, RuleReleaser releaser) {
        this.repository = repository;
        this.releaser = releaser;
    }

    @Override
    public String kind() {
        return DefinitionKinds.RULE;
    }

    @Override
    public int order() {
        return DefinitionKinds.RULE_ORDER;
    }

    @Override
    public Set<String> drafts(String orgKey) {
        return rulesOf(orgKey, Channel.DRAFT).keySet();
    }

    @Override
    public List<PendingChange> pending(String orgKey) {
        Map<String, RuleDefinition> drafts = rulesOf(orgKey, Channel.DRAFT);
        Map<String, RuleDefinition> released = rulesOf(orgKey, Channel.RELEASED);
        Set<String> ids = new TreeSet<>(drafts.keySet());
        ids.addAll(released.keySet());

        List<PendingChange> pending = new ArrayList<>();
        for (String id : ids) {
            PendingChange change = PendingChange.between(kind(), id, fingerprint(drafts.get(id)), fingerprint(released.get(id)));
            if (change != null) {
                pending.add(change);
            }
        }
        return pending;
    }

    @Override
    public List<DefinitionReference> references(String orgKey) {
        return rulesOf(orgKey, Channel.DRAFT).values().stream()
                .filter(rule -> rule.getExtendsRuleId() != null && !rule.getExtendsRuleId().isBlank())
                .map(rule -> DefinitionReference.required(kind(), rule.getId(), DefinitionKinds.RULE, rule.getExtendsRuleId()))
                .toList();
    }

    @Override
    public List<ReleaseProblem> check(String orgKey, ReleaseCandidate candidate) {
        return List.of();
    }

    @Override
    public void release(String orgKey) {
        releaser.releaseAll(orgKey);
    }

    private Map<String, RuleDefinition> rulesOf(String orgKey, Channel channel) {
        return repository.findByOrgKeyAndChannel(orgKey, channel).stream()
                .collect(Collectors.toMap(RuleDefinition::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    /** The authored content only: neither the channel, nor the version and timestamps every save moves. */
    static String fingerprint(RuleDefinition rule) {
        if (rule == null) {
            return null;
        }
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("name", rule.getName());
        content.put("description", rule.getDescription());
        content.put("context", rule.getContext());
        content.put("expression", rule.getExpression());
        content.put("severity", rule.getSeverity());
        content.put("message", rule.getMessage());
        content.put("translocoId", rule.getTranslocoId());
        content.put("extendsRuleId", rule.getExtendsRuleId());
        content.put("override", rule.isOverride());
        content.put("enabled", rule.isEnabled());
        content.put("fields", rule.getFields());
        return CanonicalJson.of(content, Set.of());
    }
}
