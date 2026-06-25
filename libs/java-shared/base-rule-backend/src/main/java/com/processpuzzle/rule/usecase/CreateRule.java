package com.processpuzzle.rule.usecase;

import com.processpuzzle.rule.adapter.inbound.RuleMapper;
import com.processpuzzle.rule.domain.RuleDefinition;
import com.processpuzzle.rule.domain.RuleDefinitionRepository;
import com.processpuzzle.rule.model.RuleDefinitionInput;
import com.processpuzzle.rule.usecase.exception.RuleAlreadyExistsException;
import com.processpuzzle.rule.usecase.service.RuleEngineSync;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CreateRule {

    private final RuleDefinitionRepository repository;
    private final RuleEngineSync ruleEngineSync;
    private final RuleExtendsValidator extendsValidator;
    private final RuleMapper mapper;

    public CreateRule(RuleDefinitionRepository repository,
                      RuleEngineSync ruleEngineSync,
                      RuleExtendsValidator extendsValidator,
                      RuleMapper mapper) {
        this.repository = repository;
        this.ruleEngineSync = ruleEngineSync;
        this.extendsValidator = extendsValidator;
        this.mapper = mapper;
    }

    public RuleDefinition execute(RuleDefinitionInput input) {
        if (repository.existsById(input.getId())) {
            throw new RuleAlreadyExistsException(input.getId());
        }
        extendsValidator.validate(input.getId(), input.getExtendsRuleId());

        RuleDefinition rule = mapper.toDomain(input);
        RuleDefinition saved = repository.save(rule);
        ruleEngineSync.register(saved);
        return saved;
    }
}
