package com.processpuzzle.rule.usecase;

import com.processpuzzle.rule.domain.RuleDefinition;
import com.processpuzzle.rule.domain.RuleDefinitionRepository;
import com.processpuzzle.rule.usecase.exception.RuleNotFoundException;
import com.processpuzzle.rule.usecase.service.RuleEngineSync;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class DeleteRule {

    private final RuleDefinitionRepository repository;
    private final RuleEngineSync ruleEngineSync;

    public DeleteRule(RuleDefinitionRepository repository, RuleEngineSync ruleEngineSync) {
        this.repository = repository;
        this.ruleEngineSync = ruleEngineSync;
    }

    public void execute(String orgKey, String id) {
        RuleDefinition existing = repository.findByOrgKeyAndId(orgKey, id)
                .orElseThrow(() -> new RuleNotFoundException(orgKey, id));

        // Only this organization's rules can extend this one, so a same-id rule in another
        // organization is not a dependent and must not block the delete.
        List<String> dependents = repository.findByOrgKeyAndExtendsRuleId(orgKey, id).stream()
                .map(RuleDefinition::getId)
                .toList();
        if (!dependents.isEmpty()) {
            throw new IllegalStateException(
                    "Cannot delete rule '" + id + "': still extended by " + dependents);
        }

        repository.delete(existing);
        ruleEngineSync.unregister(orgKey, id);
    }
}
