package com.processpuzzle.rule.usecase;

import com.processpuzzle.rule.domain.RuleDefinition;
import com.processpuzzle.rule.domain.RuleDefinitionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class FindAllRules {

    private final RuleDefinitionRepository repository;

    public FindAllRules(RuleDefinitionRepository repository) {
        this.repository = repository;
    }

    public List<RuleDefinition> execute(String context) {
        return context != null ? repository.findByContext(context) : repository.findAll();
    }
}
