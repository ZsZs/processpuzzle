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

    public RuleDefinition execute(String orgKey, RuleDefinitionInput input) {
        // An explicit existence check rather than relying on save(): with an assigned composite id
        // save() merges, so a create would silently overwrite instead of conflicting.
        if (repository.existsByOrgKeyAndId(orgKey, input.getId())) {
            throw new RuleAlreadyExistsException(orgKey, input.getId());
        }
        extendsValidator.validate(orgKey, input.getId(), input.getExtendsRuleId());

        RuleDefinition rule = mapper.toDomain(orgKey, input);
        RuleDefinition saved = repository.save(rule);
        ruleEngineSync.register(saved);
        return saved;
    }
}
