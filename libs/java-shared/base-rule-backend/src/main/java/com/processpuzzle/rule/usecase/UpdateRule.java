package com.processpuzzle.rule.usecase;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.rule.adapter.inbound.RuleMapper;
import com.processpuzzle.rule.domain.RuleDefinition;
import com.processpuzzle.rule.domain.RuleDefinitionRepository;
import com.processpuzzle.rule.model.RuleDefinitionInput;
import com.processpuzzle.rule.usecase.exception.RuleNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UpdateRule {

    private final RuleDefinitionRepository repository;
    private final RuleExtendsValidator extendsValidator;
    private final RuleMapper mapper;

    public UpdateRule(RuleDefinitionRepository repository,
                      RuleExtendsValidator extendsValidator,
                      RuleMapper mapper) {
        this.repository = repository;
        this.extendsValidator = extendsValidator;
        this.mapper = mapper;
    }

    public RuleDefinition execute(String orgKey, String id, RuleDefinitionInput input) {
        RuleDefinition existing = repository.findByOrgKeyAndIdAndChannel(orgKey, id, Channel.DRAFT)
                .orElseThrow(() -> new RuleNotFoundException(orgKey, id));
        extendsValidator.validate(orgKey, id, input.getExtendsRuleId());

        mapper.applyToDomain(input, existing);

        RuleDefinition saved = repository.save(existing);
        return saved;
    }
}
