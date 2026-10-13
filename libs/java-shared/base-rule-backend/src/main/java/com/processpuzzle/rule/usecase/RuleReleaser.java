package com.processpuzzle.rule.usecase;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.rule.domain.RuleDefinition;
import com.processpuzzle.rule.domain.RuleDefinitionRepository;
import com.processpuzzle.rule.usecase.service.RuleEngineSync;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Makes a rule's draft what the running application evaluates: copies it onto the released row, or deletes that row
 * once the draft is gone — and keeps the engine's registry in step, after the commit, since the registry holds the
 * released rules and nothing else. A designer's save no longer touches it.
 *
 * <p>Used by the release of an organization, for every rule at once, and by the sample loader, for the rules it seeds —
 * and only those, so that a designer's unreleased drafts in the same organization stay drafts.
 */
@Component
public class RuleReleaser {

    private final RuleDefinitionRepository repository;
    private final RuleEngineSync engine;

    public RuleReleaser(RuleDefinitionRepository repository, RuleEngineSync engine) {
        this.repository = repository;
        this.engine = engine;
    }

    /** Releases every rule of the organization, and removes the released ones whose draft is gone. */
    @Transactional
    public void releaseAll(String orgKey) {
        Set<String> ids = new TreeSet<>();
        repository.findByOrgKey(orgKey).forEach(rule -> ids.add(rule.getId()));
        ids.forEach(id -> releaseOne(orgKey, id));
    }

    /** Releases one rule. */
    @Transactional
    public void release(String orgKey, String id) {
        releaseOne(orgKey, id);
    }

    private void releaseOne(String orgKey, String id) {
        RuleDefinition draft = repository.findByOrgKeyAndIdAndChannel(orgKey, id, Channel.DRAFT).orElse(null);
        RuleDefinition released = repository.findByOrgKeyAndIdAndChannel(orgKey, id, Channel.RELEASED).orElse(null);
        if (draft == null) {
            if (released != null) {
                repository.delete(released);
                engine.unregisterAfterCommit(orgKey, id);
            }
            return;
        }
        RuleDefinition saved;
        if (released == null) {
            saved = repository.save(RuleDefinition.releasedCopyOf(draft));
        } else {
            released.releaseFrom(draft);
            saved = repository.save(released);
        }
        engine.registerAfterCommit(saved);
    }
}
