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

    public void execute(String id) {
        RuleDefinition existing = repository.findById(id)
                .orElseThrow(() -> new RuleNotFoundException(id));

        List<String> dependents = repository.findAll().stream()
                .filter(r -> id.equals(r.getExtendsRuleId()))
                .map(RuleDefinition::getId)
                .toList();
        if (!dependents.isEmpty()) {
            throw new IllegalStateException(
                    "Cannot delete rule '" + id + "': still extended by " + dependents);
        }

        repository.delete(existing);
        ruleEngineSync.unregister(id);
    }
}
