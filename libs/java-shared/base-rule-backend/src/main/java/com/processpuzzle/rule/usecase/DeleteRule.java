package com.processpuzzle.rule.usecase;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.rule.domain.RuleDefinition;
import com.processpuzzle.rule.domain.RuleDefinitionRepository;
import com.processpuzzle.rule.usecase.exception.RuleNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class DeleteRule {

    private final RuleDefinitionRepository repository;

    public DeleteRule(RuleDefinitionRepository repository) {
        this.repository = repository;
    }

    public void execute(String orgKey, String id) {
        RuleDefinition existing = repository.findByOrgKeyAndIdAndChannel(orgKey, id, Channel.DRAFT)
                .orElseThrow(() -> new RuleNotFoundException(orgKey, id));

        // Only this organization's rules can extend this one, so a same-id rule in another
        // organization is not a dependent and must not block the delete.
        List<String> dependents = repository.findByOrgKeyAndExtendsRuleIdAndChannel(orgKey, id, Channel.DRAFT).stream()
                .map(RuleDefinition::getId)
                .toList();
        if (!dependents.isEmpty()) {
            throw new IllegalStateException(
                    "Cannot delete rule '" + id + "': still extended by " + dependents);
        }

        repository.delete(existing);
    }
}
