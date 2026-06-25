package com.processpuzzle.rule.usecase;

import com.processpuzzle.rule.domain.RuleDefinition;
import com.processpuzzle.rule.domain.RuleDefinitionRepository;
import com.processpuzzle.rule.usecase.exception.RuleNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FindRule {

    private final RuleDefinitionRepository repository;

    public FindRule(RuleDefinitionRepository repository) {
        this.repository = repository;
    }

    public RuleDefinition execute(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuleNotFoundException(id));
    }
}
