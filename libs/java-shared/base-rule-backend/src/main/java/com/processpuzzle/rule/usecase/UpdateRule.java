package com.processpuzzle.rule.usecase;

import com.processpuzzle.rule.adapter.inbound.RuleMapper;
import com.processpuzzle.rule.domain.RuleDefinition;
import com.processpuzzle.rule.domain.RuleDefinitionRepository;
import com.processpuzzle.rule.model.RuleDefinitionInput;
import com.processpuzzle.rule.usecase.exception.RuleNotFoundException;
import com.processpuzzle.rule.usecase.service.RuleEngineSync;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UpdateRule {

    private final RuleDefinitionRepository repository;
    private final RuleEngineSync ruleEngineSync;
    private final RuleExtendsValidator extendsValidator;
    private final RuleMapper mapper;

    public UpdateRule(RuleDefinitionRepository repository,
                      RuleEngineSync ruleEngineSync,
                      RuleExtendsValidator extendsValidator,
                      RuleMapper mapper) {
        this.repository = repository;
        this.ruleEngineSync = ruleEngineSync;
        this.extendsValidator = extendsValidator;
        this.mapper = mapper;
    }

    public RuleDefinition execute(String id, RuleDefinitionInput input) {
        RuleDefinition existing = repository.findById(id)
                .orElseThrow(() -> new RuleNotFoundException(id));
        extendsValidator.validate(id, input.getExtendsRuleId());

        mapper.applyToDomain(input, existing);

        RuleDefinition saved = repository.save(existing);
        ruleEngineSync.register(saved);
        return saved;
    }
}
